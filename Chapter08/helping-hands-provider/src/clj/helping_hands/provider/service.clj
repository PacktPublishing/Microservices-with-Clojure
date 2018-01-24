(ns helping-hands.provider.service
  (:require [helping-hands.provider.core :as core]
            [cheshire.core :as jp]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.chain :as chain]
            [ring.util.response :as ring-resp]))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

(defn- get-uid
  [token]
  (when (and (string? token) (not (empty? token)))
    ;; validate token
    {"uid" "hhuser"}))

(def auth
  {:name ::auth

   :enter
   (fn [context]
     (let [token (-> context :request :headers (get "token"))]
       (if-let [uid (and (not (nil? token)) (get-uid token))]
         (assoc-in context [:request :tx-data :user] uid)
         (chain/terminate
          (assoc context
                 :response {:status 401
                            :body "Auth token not found"})))))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

(def gen-events
  {:name ::events

   :enter
   (fn [context]
     (if (:response context)
       context
       (assoc context :response {:status 200 :body "SUCCESS"})))

   :error
   (fn [context ex-info]
     (assoc context
            :response {:status 500
                       :body (.getMessage ex-info)}))})

;; Tabular routes
(def routes #{["/providers/:id"
               :get (conj common-interceptors `auth `core/validate-id
                          `core/get-provider `gen-events)
               :route-name :provider-get]
              ["/providers/:id"
               :put (conj common-interceptors `auth `core/validate-id
                          `core/upsert-provider `gen-events)
               :route-name :provider-put]
              ["/providers/:id/rate"
               :put (conj common-interceptors `auth `core/validate-id
                          `core/upsert-provider `gen-events)
               :route-name :provider-rate]
              ["/providers"
               :post (conj common-interceptors `auth `core/validate
                           `core/create-provider `gen-events)
               :route-name :provider-post]
              ["/providers/:id"
               :delete (conj common-interceptors `auth `core/validate-id
                             `core/delete-provider `gen-events)
               :route-name :provider-delete]})

;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})

