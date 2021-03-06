(ns bluegenes.utils
  (:require [clojure.string :as string]
            [clojure.data.xml :as xml]
            [imcljs.query :as im-query]
            [bluegenes.version :as version]
            [bluegenes.components.icons :refer [icon]]
            [markdown-to-hiccup.core :as md]))

(defn md-paragraph
  "Returns the `[:p]` hiccup for a specified markdown string paragraph.
  Usage:
      [:div (parse-markdown \"Foo *bar* [baz](http://baz.com)\")]
  Note that only the first paragraph in the markdown string will be parsed;
  any other elements before or after will be ignored, and so will any proceeding
  paragraphs."
  [md-string]
  (some-> md-string md/md->hiccup md/component (md/hiccup-in :div :p)))

(defn uncamel
  "Uncamel case a string. Example: thisIsAString -> This is a string"
  [s]
  (if-not (string/blank? s)
    (as-> s $
      (string/split $ #"(?=[A-Z][^A-Z])")
      (string/join " " $)
      (string/capitalize $))
    s))

(defn read-origin
  "Read the origin class from a query, and infer it if it's missing."
  [query]
  (if-let [origin (:from query)]
    origin
    (first (string/split (first (:select query)) #"\."))))

(defn kw->str
  [kw]
  (if (keyword? kw)
    (str (namespace kw)
         (when (namespace kw) "/")
         (name kw))
    (do (assert (string? kw) "This function takes only a keyword or string.")
        kw)))

(defn read-registry-mine
  "Grab the most important data from a mine object retrieved from the registry.
  This is how a mine is initially created in `(:mines app-db)`, before it is
  populated with the responses from fetching assets."
  [reg-mine]
  {:service {:root (:url reg-mine)}
   :name (:name reg-mine)
   :id (-> reg-mine :namespace keyword)
   :logo (-> reg-mine :images :logo)})

(defn read-xml-query
  "Read an InterMine PathQuery in XML into an EDN Clojure map.
  Will throw on invalid XML."
  [xml-query]
  (let [xml-map         (xml/parse-str xml-query)
        select          (string/split (get-in xml-map [:attrs :view] " ") #" ")
        _               (when (empty? select) (throw (js/Error. "Invalid PathQuery XML")))
        from            (not-empty (first (string/split (first select) #"\.")))
        constraintLogic (get-in xml-map [:attrs :constraintLogic])
        orderBy         (let [{:keys [sortOrder orderBy]} (:attrs xml-map)
                              pairs (partition 2 (string/split (or sortOrder orderBy) #" "))]
                          (mapv (fn [[path dir]]
                                  {(keyword path) (string/upper-case dir)}) pairs))
        joins           (into []
                              (comp (filter (comp #{:join} :tag))
                                    (filter (comp #{"OUTER"} :style :attrs))
                                    (map (comp :path :attrs)))
                              (:content xml-map))
        where           (into []
                              (comp (filter (comp #{:constraint} :tag))
                                    (map (fn [{:keys [attrs content]}]
                                           (cond-> attrs
                                             (not-empty content)
                                             ;; Handle ONE OF constraints.
                                             (assoc :values
                                                    (->> content
                                                         (filter (comp #{:value} :tag))
                                                         (mapcat :content)))))))
                              (:content xml-map))]
    {:from from
     :select select
     :orderBy orderBy
     :constraintLogic constraintLogic
     :joins joins
     :where where}))

(defn suitable-entities
  "Removes key-value pairs from an entities map which don't adhere to config.
  Can also be used to check whether a tool should be displayed, as it will
  return nil if no entity is suitable at all.
  1. Check that the tool's API version matches this Bluegenes.
  2. Check that the tool's model dependencies are present.
  3. Remove entity pairs which don't match tool's accepted formats.
  4. Pick entity pairs that match tool's classes (all when `*` wildcard is used)."
  [model entities config]
  (when-let [{:keys [accepts classes depends version]
              :or {version 1}} config]
    (when (and (= version version/tool-api)
               (every? #(contains? model %) (map keyword depends)))
      (as-> entities $
        (into {} (filter (comp (set accepts) :format val)) $)
        (if (some #{"*"} classes)
          $
          (select-keys $ (map keyword classes)))
        (not-empty $)))))

(defn version-string->vec
  "Converts a version string consisting of one or more whole numbers separated
  by non-numeric characters into a vector of integers. Returns nil if the
  version string can't be interpreted."
  [vstring]
  (some->> vstring
           (re-seq #"\d+")
           (mapv #(js/parseInt % 10))))

(defn compatible-version?
  "Returns whether `version` is compatible with `required-version`, meaning it
  must be greater than or equal. Versions can be either a string or vector of
  integers. Will return false if versions have differing amount of subversions."
  [required-version version]
  (let [version (cond-> version
                  (string? version) version-string->vec)
        required-version (cond-> required-version
                           (string? required-version) version-string->vec)]
    (if (= (count version)
           (count required-version))
      (reduce
       (fn [_ [index v]]
         (let [rv (nth required-version index)]
           (cond
             (< v rv) (reduced false)
             (> v rv) (reduced true)
             ;; This assures that `true` is returned if all subversions are equal.
             :else true)))
       nil
       (map-indexed vector version))
      false)))

(defn parse-template-rank [rank]
  (let [rank-num (js/parseInt rank)]
    ;; Template ranks come back as strings, either "unranked", or
    ;; integers that have become stringy, e.g. "12". If we don't parse
    ;; them into ints, the order becomes 1, 11, 12, 2, 23, 25, 3, etc.
    ;; but we also need to handle the genuine strings, which become NaN
    ;; when we try to parse them.
    (if (.isNaN js/Number rank-num)
      ;; unranked == last please.
      ;; I sincerely hope we never have 100k templates
      99999
      ;; if it's a number, just return it.
      rank-num)))

(defn ascii-arrows
  "Returns a seq of all arrows present in a template title.
  Useful for checking whether there are any arrows present."
  [s]
  (re-seq #"(?:-+>|<-+)" s))

(defn flatten-seq
  "Works like flatten except it will only remove seqs; keeping vectors, lists
  and other sequential things. This is useful when you have hiccup with seqs
  interwoven and want to clean it up to get a flat sequence of elements."
  [x]
  (filter (complement seq?)
          (rest (tree-seq seq? seq x))))

(defn ascii->svg-arrows
  "Replaces arrows in template titles with prettier svg icons."
  [s]
  (flatten-seq
   (interpose [icon "arrow-right"]
              (map (fn [part]
                     (interpose [icon "arrow-left"]
                                (map (fn [subpart]
                                       [:span subpart])
                                     (string/split part #"<-+"))))
                   (string/split s #"-+>")))))
