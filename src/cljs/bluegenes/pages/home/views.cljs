(ns bluegenes.pages.home.views
  (:require [re-frame.core :as re-frame]
            [bluegenes.components.search.typeahead :as search]
            [bluegenes.route :as route]))

(defn searchbox []
  [:div.search
   [search/main]
   [:div.info
    [:svg.icon.icon-info [:use {:xlinkHref "#icon-info"}]]
    " Search for genes, proteins, pathways, ontology terms, authors, etc."]])

(defn lists []
  [:div.feature.lists
   [:h3 "Lists"]
   [:div.piccie [:a {:href (route/href ::route/upload)}
                 [:svg.icon.icon-summary [:use {:xlinkHref "#icon-summary"}]]]]
   [:div
    [:a {:href (route/href ::route/lists)}
     "View"]
    [:a {:href (route/href ::route/upload)}
     "Upload"]]])

(defn templates []
  [:div.feature.templates
   [:h3 "Templates"]
   [:div.piccie
    [:a {:href (route/href ::route/templates)}
     [:svg.icon.icon-search [:use {:xlinkHref "#icon-search"}]]]]
   [:div
    [:a {:href (route/href ::route/templates)}
     "Browse"]]])

(defn main []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [:div.approot
       [:div.home.circles
        [searchbox]
        [:div.features
         [lists]
         [templates]]]])))
