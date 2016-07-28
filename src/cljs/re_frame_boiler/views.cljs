(ns re-frame-boiler.views
  (:require [re-frame.core :as re-frame]
            [json-html.core :as json-html]
            [re-frame-boiler.components.nav :as nav]))

(defn debug-panel []
  (let [app-db (re-frame/subscribe [:app-db])]
    (fn []
      [:div (json-html/edn->hiccup @app-db)])))

;; home

(defn home-panel []
  (let [name (re-frame/subscribe [:name])]
    (fn []
      [:div [:h1 (str "Hello from " @name)]])))

;; about

(defn about-panel []
  (fn []
    [:div "This is the About Page."
     [:div [:a.callout {:href "#/"} "go to Home Page"] ]]))

;; main

(defmulti panels identity)
(defmethod panels :home-panel [] [home-panel])
(defmethod panels :about-panel [] [about-panel])
(defmethod panels :debug-panel [] [debug-panel])
(defmethod panels :default [] [:div])

(defn show-panel
  [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [:div
       [nav/main]
       [:div.container
        [:div.panel [show-panel @active-panel]]]])))