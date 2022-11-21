(ns cloudflare.dnslink
  (:require
    [clojure.string :as str]
    [org.httpkit.sni-client :as sni-client]
    [org.httpkit.client :as http]
    [cheshire.core :as json]))

(alter-var-root #'org.httpkit.client/*default-client*
                (fn [_] sni-client/default-client))

(def api-root "https://api.cloudflare.com/client/v4")

(defn api-client
  [api-token]
  {:root-url api-root
   :opts     {:headers {"Authorization" (str "Bearer " api-token)}}})

(defn merge-opts
  [& opts]
  ;; merge-with merge gives us two-level deep merge, which is hopefully
  ;; enough for http-kit request params
  (apply merge-with merge opts))

(defn decode-body
  [resp]
  (if (:body resp)
    (update resp :body json/decode true)
    resp))

(defn encode-body
  [req]
  (if (:body req)
    (update req :body json/encode)
    req))

(defn api-req
  [method client resource & [opts]]
  (loop [page    1
         results []]
    (let [url         (str (:root-url client) "/" resource)
          res         (-> {:url url, :method method, :query-params {:page page}}
                          (merge-opts opts (:opts client))
                          encode-body
                          ((fn [o] (println "Req opts:" (pr-str o)) o))
                          http/request
                          deref
                          decode-body)
          _           (when-not (= 200 (:status res))
                        (throw (ex-info (str "Cloudflare returned error: "
                                             (pr-str res))
                                        res)))
          result-info (some-> res :body :result_info)
          result      (some-> res :body :result)
          results     (if (sequential? result)
                        (into results result)
                        (conj results result))]
      (if (and result-info (< page (:total_pages result-info)))
        (recur (inc page) results)
        results))))

(def api-get
  (partial api-req :get))

(def api-post
  (partial api-req :post))

(def api-patch
  (partial api-req :patch))

(defn zones
  [client]
  (api-get client "zones"))

(defn zone-name->id
  [client zone-name]
  (let [zones (zones client)]
    (:id (some #(when (= zone-name (:name %)) %) zones))))

(defn dns-records
  [client zone-id]
  (api-get client (str "zones/" zone-id "/dns_records")
           {:query-params {:type "TXT"}}))

(defn dnslink-record
  [client zone-id]
  (let [records (dns-records client zone-id)]
    (some #(when (str/starts-with? (:content %) "dnslink=") %) records)))

(defn update-dnslink-record
  [client zone-id record-id link]
  (api-patch client (str "zones/" zone-id "/dns_records/" record-id)
             {:body {:content (str "dnslink=" link)}}))

(defn create-dnslink-record
  [client zone-id link]
  (api-post client (str "zones/" zone-id "/dns_records")
            {:body {:type    "TXT", :name "_dnslink"
                    :content (str "dnslink=" link)}}))

(defn set-dnslink-record
  [client zone-id link]
  (if-let [existing-record (dnslink-record client zone-id)]
    (update-dnslink-record client zone-id (:id existing-record) link)
    (create-dnslink-record client zone-id link)))
