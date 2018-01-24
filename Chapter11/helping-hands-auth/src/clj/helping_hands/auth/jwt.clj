(ns helping-hands.auth.jwt
  "JWT Implementation for Auth Service"
  (:require [cheshire.core :as jp])
  (:import [com.nimbusds.jose EncryptionMethod
            JWEAlgorithm JWSAlgorithm
            JWEDecrypter JWEEncrypter
            JWEHeader$Builder JWEObject Payload]
           [com.nimbusds.jose.crypto
            AESDecrypter AESEncrypter]
           [com.nimbusds.jose.jwk KeyOperation KeyUse
            OctetSequenceKey OctetSequenceKey$Builder]
           [com.nimbusds.jwt JWTClaimsSet JWTClaimsSet$Builder]
           [com.nimbusds.jwt.proc DefaultJWTClaimsVerifier]
           [com.nimbusds.jose.util Base64URL]
           [java.util Date]
           [javax.crypto KeyGenerator]
           [javax.crypto.spec SecretKeySpec]))

(def ^:cons khash-256 "SHA-256")

(defonce ^:private kgen-aes-128
  (let [keygen (KeyGenerator/getInstance "AES")
        _ (.init keygen 128)]
    keygen))

(defonce ^:private alg-a128kw
  (JWEAlgorithm/A128KW))

(defonce ^:private enc-a128cbc_hs256
  (EncryptionMethod/A128CBC_HS256))

(defn get-secret
  "Gets the secret key"
  ([] (get-secret kgen-aes-128))
  ([kgen]
   ;; must be created iff the key hasn't
   ;; been creaed earlier. Create once and
   ;; persist in an external database
   (.generateKey kgen)))

(defn get-secret-jwk
  "Generates a new JSON Web Key (JWK)"
  [{:keys [khash kgen alg] :as enc-impl} secret]
  ;; must be created iff the key hasn't
  ;; been creaed earlier. Create once and
  ;; persist in an external database
  (.. (OctetSequenceKey$Builder. secret)
      (keyIDFromThumbprint (or khash khash-256))
      (algorithm (or alg alg-a128kw))
      (keyUse (KeyUse/ENCRYPTION))
      (build)))

(defn enckey->secret
  "Converts JSON Web Key (JWK) to the secret key"
  [{:keys [k kid alg] :as enc-key}]
  (.. (OctetSequenceKey$Builder. k)
      (keyID kid)
      (algorithm (or alg alg-a128kw))
      (keyUse (KeyUse/ENCRYPTION))
      (build)
      (toSecretKey "AES")))

(defn- create-payload
  "Creates a payload as JWT Claims"
  [{:keys [user roles] :as params}]
  (let [ts (System/currentTimeMillis)
        claims (.. (JWTClaimsSet$Builder.)
                   (issuer "Packt")
                   (subject "HelpingHands")
                   (audience "https://www.packtpub.com")
                   (issueTime (Date. ts))
                   (expirationTime (Date. (+ ts 120000)))
                   (claim "user" user)
                   (claim "roles" roles)
                   (build))]
    (.toJSONObject claims)))

(defn create-token
  "Creates a new token with the given payload"
  [{:keys [user roles alg enc] :as params} secret]
  (let [enckey (get-secret-jwk params secret)
        payload (create-payload {:user user :roles roles})
        passphrase (JWEObject.
                    (.. (JWEHeader$Builder.
                         (or alg alg-a128kw)
                         (or enc enc-a128cbc_hs256))
                        (build))
                    (Payload. payload))
        encrypter (AESEncrypter. enckey)
        _ (.encrypt passphrase encrypter)]
    (.serialize passphrase)))

(defn read-token
  "Decrypts the given token with the said algorithm
   Throws BadJWTException is token is invalid or expired"
  [token secret]
  (let [passphrase (JWEObject/parse token)
        decrypter (AESDecrypter. secret)
        _ (.decrypt passphrase decrypter)
        payload (.. passphrase getPayload toString)
        claims (JWTClaimsSet/parse payload)
        ;; throws exception if the token is invalid
        _ (.verify (DefaultJWTClaimsVerifier.) claims)]
    (jp/parse-string payload)))
