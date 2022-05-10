(ns swing-files.core
  (:require [clojure.string :as str]
            [clojure.pprint :as pp])
  (:import (javax.swing JFrame JTextArea JTable JScrollPane BorderFactory
                        JTextField JPanel JButton)
           (java.awt Dimension BorderLayout FlowLayout)
           (java.io File)
           [org.apache.commons.io FileUtils]
           (java.time.format DateTimeFormatter)
           (java.time LocalDateTime Instant ZoneId)
           (java.awt.event MouseAdapter ActionListener)
           (javax.swing.plaf.metal MetalLookAndFeel OceanTheme)
           (javax.swing.event ListSelectionListener))
  (:gen-class))

(defn dir? [file]
  (.isDirectory file))

(defn set-psize [this w h]
  (.setPreferredSize this (Dimension. w h)))

(defn set-columns-width [^JTable table & args]
  (let [model (.getColumnModel ^JTable table)]
    (doseq [[i width] (map-indexed vector args)]
      (.setPreferredWidth (.getColumn model i) width))))

(defn file-basic-info [a-file]
  [(.getName a-file)
   (.format (DateTimeFormatter/ofPattern "dd.MM.yyyy HH:mm:ss")
            (LocalDateTime/ofInstant
              (Instant/ofEpochMilli (.lastModified a-file))
              (ZoneId/systemDefault)))
   (if (dir? a-file) "Directory" "File")
   (FileUtils/byteCountToDisplaySize
     ^long (.length a-file))])

(defn table-data [absolute-path]
  (->> (seq (.listFiles absolute-path))
       (sort-by (juxt #(if (dir? %) "Directory" "File")
                      #(.getName %)))
       (map file-basic-info)))

(defn children-count [a-file]
  (->> (seq (.listFiles a-file))
       ((juxt filter remove) dir?)
       (map count)))

; absolute file
(def file-info
  #(str/join
     "\n"
     (list*
       (str (if (dir? %) "\uD83D\uDCC1 " "\uD83D\uDDCE ")
            (.getName %))
       (if (dir? %)
         (pp/cl-format nil "~{~s director~:@p, ~s file~:p~}" (children-count %))
         "")
       (->> ["canRead" (.canRead %)
             "canWrite" (.canWrite %)
             "length" (.length %)
             "isDirectory" (dir? %)
             "isFile" (.isFile %)
             "isHidden" (.isHidden %)]
            (partition 2 2)
            (map (fn [[s data]] (str s ":\t" data)))))))

(defn -main [& args]
  (let [height 350
        width 850

        current-absolute-file (volatile! (.getAbsoluteFile (File. "")))

        info (doto (JTextArea. ^String (file-info (.getAbsoluteFile @current-absolute-file)))
               (set-psize (* 1.5 (/ width 6))
                          (* 2 (/ height 4)))
               (.setEditable false)
               (.setBorder (BorderFactory/createEmptyBorder 10 10 10 10)))

        scroll (doto (JScrollPane.)
                 (set-psize (* 4 (/ width 6))
                            (* 2.85 (/ height 4))))

        filename (doto (JTextField. (.getAbsolutePath @current-absolute-file))
                   (set-psize (* 8 (/ width 10)) 30))

        button (JButton. "\u2191")

        top-line (doto (JPanel.)
                   (.setLayout (FlowLayout.))
                   (set-psize width (/ height 10))
                   (.add filename)
                   (.add button))

        center-line (doto (JPanel.)
                      (.setLayout (doto (FlowLayout.)
                                    (.setAlignOnBaseline true)))
                      (set-psize width (* 9 (/ height 10)))
                      (.add scroll)
                      (.add info))

        frame (doto (JFrame. "File Manager")
                (set-psize width height)
                (.add top-line BorderLayout/PAGE_START)
                (.add center-line BorderLayout/CENTER))]

    (letfn [(make-data-table [a-file]
              (let [table (doto (JTable. (to-array-2d (table-data a-file))
                                         (to-array ["Name" "Last Modified" "Type" "Size"]))
                            (.setDefaultEditor Object nil)
                            (.setAutoCreateRowSorter true)
                            (.setShowGrid false)
                            (set-columns-width (/ width 3)
                                               (/ width 3)
                                               (/ width 6)
                                               (/ width 6)))]

                (doto (.getSelectionModel table)
                  (.addListSelectionListener
                    (proxy [ListSelectionListener] []
                      (valueChanged [event]
                        (let [file (File. (.getAbsolutePath @current-absolute-file)
                                          ^String (.getValueAt table
                                                               (.getSelectedRow table)
                                                               0))]
                          (.setText info
                                    (file-info (.getAbsoluteFile file))))))))
                (doto table
                  (.addMouseListener
                    (proxy [MouseAdapter] []
                      (mouseClicked [event]
                        (if (= 2 (.getClickCount event))
                          (do (.setViewportView
                                scroll
                                (make-data-table (vreset! current-absolute-file
                                                          (File. (.getAbsolutePath @current-absolute-file)
                                                                 ^String (.getValueAt table (.getSelectedRow table) 0)))))
                              (.setText filename (.getAbsolutePath @current-absolute-file)))

                          (let [file (File. (.getAbsolutePath @current-absolute-file)
                                            ^String (.getValueAt table
                                                                 (.getSelectedRow table)
                                                                 0))]
                            (.setText info
                                      (file-info (.getAbsoluteFile file)))))))))))]

      (.setView (.getViewport scroll) (make-data-table @current-absolute-file))
      (.addActionListener button (proxy [ActionListener] []
                                   (actionPerformed [_]
                                     (when-let [parent (.getParentFile @current-absolute-file)]
                                       (.setViewportView
                                         scroll
                                         (make-data-table (vreset! current-absolute-file parent)))
                                       (.setText filename (.getAbsolutePath @current-absolute-file))))))

      (MetalLookAndFeel/setCurrentTheme (OceanTheme.))
      (doto frame
        .pack
        (.setVisible true)))))