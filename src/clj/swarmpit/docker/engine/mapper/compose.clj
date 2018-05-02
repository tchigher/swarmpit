(ns swarmpit.docker.engine.mapper.compose
  (:require [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [swarmpit.utils :refer [clean select-keys*]]
            [swarmpit.docker.utils :refer [trim-stack]]
            [swarmpit.yaml :refer [->yaml]])
  (:refer-clojure :exclude [alias]))

(defn in-stack?
  [stack-name map]
  (= stack-name (:stack map)))

(defn alias
  [key stack-name map]
  (let [name (get map key)]
    (if (in-stack? stack-name map)
      (trim-stack stack-name name)
      name)))

(defn group
  [stack-name fn coll]
  (->> coll
       (map #(fn stack-name %))
       (into {})))

(defn variables
  [variables]
  (map #(str (:name %) "=" (:value %)) variables))

(defn resource
  [{:keys [cpu memory]}]
  {:cpus   (when (< 0 cpu) (str cpu))
   :memory (when (< 0 memory) (str memory "M"))})

(defn remove-defaults
  [map defaults]
  (->> (keys defaults)
       (filter #(= (% defaults) (% map)))
       (apply dissoc map)))

(defn service
  [stack-name service]
  {(keyword (alias :serviceName stack-name service))
   {:image       (-> service :repository :image)
    :environment (-> service :variables (variables))
    :ports       (->> service :ports
                      (map #(str (:hostPort %) ":" (:containerPort %) (when (= "udp" (:protocol %)) "/udp"))))
    :volumes     (->> service :mounts
                      (map #(str (alias :host stack-name %) "=" (:containerPath %) (when (:readOnly %) ":ro"))))
    :networks    (->> service :networks (map #(alias :networkName stack-name %)))
    :deploy      {:mode           (when-not (= "replicated" (:mode service)) (:mode service))
                  :replicas       (some-> (:replicas service) (#(when (< 1 %) %)))
                  :labels         (-> service :labels (variables))
                  :update_config  (-> service :deployment :update
                                      (rename-keys {:failureAction :failure_action})
                                      (update :delay #(str % "s"))
                                      (remove-defaults
                                        {:parallelism    1
                                         :delay          "0s"
                                         :order          "stop-first"
                                         :failure_action "pause"}))
                  :restart_policy (-> service :deployment :restartPolicy
                                      (rename-keys {:attempts :max_attempts})
                                      (update :delay #(str % "s"))
                                      (remove-defaults
                                        {:condition    "any"
                                         :delay        "5s"
                                         :max_attempts 0}))
                  :placement      {:constraints (->> service :deployment :placement (map :rule))}
                  :resources      {:reservations (-> service :resources :reservation resource)
                                   :limits       (-> service :resources :limit resource)}}}})

(defn network
  [stack-name net]
  {(keyword (alias :networkName stack-name net))
   (if (in-stack? stack-name net)
     {:driver      (:driver net)
      :internal    (when (:internal net) true)
      :driver_opts (:options net)}
     {:external true})})

(defn volume
  [stack-name volume]
  {(keyword (alias :volumeName stack-name volume))
   (if (in-stack? stack-name volume)
     {:driver      (:driver volume)
      :driver_opts (:options volume)}
     {:external true})})

(defn secret
  [_ secret]
  {(keyword (:secretName secret))
   {:external true}})

(defn config
  [_ config]
  {(keyword (:configName config))
   {:external true}})

(defn ->compose
  [stack]
  (let [name (:stackName stack)]
    (-> {:version  "3"
         :services (group name service (:services stack))
         :networks (group name network (:networks stack))
         :volumes  (group name volume (:volumes stack))
         :configs  (group name config (:configs stack))
         :secrets  (group name secret (:secrets stack))}
        (clean))))