(ns pixel-art.palette.gimp-file
  (:require ["tinycolor2" :as tinycolor]
            [clojure.string :as string]))

(defn palette->file-desc [palette]
  {:file-name (str (:name palette) ".gpl")
   :content (->> (concat ["GIMP Palette"
                          (str "Name: " (:name palette))
                          "Columns: 0"]
                         (map (fn [color]
                                (let [{:keys [r g b]} (js->clj (. (tinycolor color) toRgb) :keywordize-keys true)]
                                  (string/join " " [r g b "Untitled"])))
                              (:colors palette)))
                 (string/join "\n"))})

(defn parse-content [content]
  (let [lines (string/split-lines content)
        [first-line second-line third-line] lines]
    (if (= first-line "GIMP Palette")
      (if-let [name (some-> (re-find #"Name:\s*(\S.*)\s*" (or second-line ""))
                            second
                            string/trim)]
        (let [columns (some-> (re-find #"Columns:\s*(\d+)" (or third-line ""))
                              second
                              parse-double)
              color-lines (->> lines
                               (drop (if (some? columns) 3 2))
                               (remove #(re-find #"^\s*#" %)) ;; comment lines
                               (map string/trim))
              colors (->> color-lines
                          (keep (fn [line]
                                  (when-let [[r-str g-str b-str] (rest (re-find #"^\s*(\d+)\s+(\d+)\s+(\d+)\s+" line))]
                                    (when-let [[r g b] (->> [r-str g-str b-str]
                                                            (map parse-double)
                                                            (filter #(and (>= % 0) (<= % 255)))
                                                            (#(when (= (count %) 3) %)))]
                                      (str "rgb(" r ", " g ", " b ")")))))
                          vec)]
          {:ok {:name name :colors colors}})
        {:error "Second line are invalid name line"})
      {:error "First line should be 'GIMP Palette'"})))
