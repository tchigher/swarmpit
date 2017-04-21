(ns swarmpit.api
  (:require [swarmpit.docker.client :as dc]
            [swarmpit.registry.client :as rc]
            [swarmpit.domain :as dom]
            [swarmpit.utils :refer [in?]]))

;;; Service API

(defn services
  []
  (->> (dc/get "/services")
       (dom/<-services)))

(defn service
  [service-id]
  (->> (str "/services/" service-id)
       (dc/get)
       (dom/<-service)))

(defn delete-service
  [service-id]
  (->> (str "/services/" service-id)
       (dc/delete)))

(defn create-service
  [service]
  (->> (dom/->service service)
       (dc/post "/services/create")))

(defn update-service
  [service-id service]
  (->> (dom/->service service)
       (dc/post (str "/services/" service-id "/update?version=" (:version service)))))

;;; Network API

(defn networks
  []
  (->> (dc/get "/networks")
       (dom/<-networks)))

(defn network
  [network-id]
  (->> (str "/networks/" network-id)
       (dc/get)
       (dom/<-network)))

(defn delete-network
  [network-id]
  (->> (str "/networks/" network-id)
       (dc/delete)))

(defn create-network
  [network]
  (->> (dom/->network network)
       (dc/post "/networks/create")))

;;; Node API

(defn nodes
  []
  (->> (dc/get "/nodes")
       (dom/<-nodes)))

(defn node
  [node-id]
  (->> (str "/nodes/" node-id)
       (dc/get)
       (dom/<-node)))

;;; Task API

(defn tasks
  []
  (->> (dc/get "/tasks")
       (dom/<-tasks)))

(defn task
  [task-id]
  (->> (str "/tasks/" task-id)
       (dc/get)
       (dom/<-task)))