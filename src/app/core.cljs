(ns app.core
  (:require
   ["@dimforge/rapier3d-compat" :as rapier]
   ["three/addons/controls/OrbitControls.js" :refer [OrbitControls]]
   ["three/addons/environments/RoomEnvironment.js" :refer [RoomEnvironment]]
   ["three/addons/loaders/GLTFLoader.js" :refer [GLTFLoader]]
   [three :refer [ACESFilmicToneMapping Box3 BufferAttribute BufferGeometry
                  Color LineBasicMaterial LineSegments PerspectiveCamera
                  PMREMGenerator Scene Vector3 Quaternion Euler WebGLRenderer]]))

(defn- prepare-container! [id]
  (let [container (js/document.getElementById id)]
    (set! (.-innerHTML container) "")
    container))

(def system
  (let [container (prepare-container! "app")
        inner-width (.-innerWidth js/window)
        inner-height (.-innerHeight js/window)
        scene (new Scene)
        camera (new PerspectiveCamera 75 (/ inner-width inner-height) 0.1 1000)
        renderer (new WebGLRenderer)
        environment (new RoomEnvironment)
        pmremGenerator (new PMREMGenerator renderer)
        generated-env (.fromScene pmremGenerator environment)]
    (set! (.-background scene) (new Color 0xbbbbbb))
    (set! (.-environment scene) (.-texture generated-env))
    (set! (.-toneMapping renderer) ACESFilmicToneMapping)
    (set! (.-toneMappingExposure renderer) 0.3)
    (.setSize renderer inner-width inner-height)
    (.appendChild container (.-domElement renderer))
    (.set (-> camera .-position) 0 5 -10)
    (new OrbitControls camera (.-domElement renderer))
    {:scene scene
     :camera camera
     :renderer renderer}))

(defn load-async [resource]
  (let [loader (new GLTFLoader)]
    (new js/Promise
         (fn [resolve reject]
           (.load loader
                  resource
                  (fn [gltf] (resolve gltf))
                  (fn [xhr] (js/console.log (str (* (/ xhr.loaded xhr.total) 100) "% loaded")))
                  (fn [error] (reject error)))))))

(defn sync-mesh-collider [^js mesh ^js collider]
  (let [^js collider-translation (.translation collider)
        ^js collider-rotation (.rotation collider)]
    (.set (.-position mesh)
          (.-x collider-translation)
          (.-y collider-translation)
          (.-z collider-translation))
    (.setRotationFromQuaternion mesh
                                (new Quaternion
                                     (.-x collider-rotation)
                                     (.-y collider-rotation)
                                     (.-z collider-rotation)
                                     (.-w collider-rotation)))
    (.updateMatrix mesh)))

(defn debug-render [^js lines ^js world]
  (let [buffers (.debugRender world)]
    (.setAttribute (.-geometry lines) "position" (new BufferAttribute (.-vertices buffers) 3))
    (.setAttribute (.-geometry lines) "color" (new BufferAttribute (.-colors buffers) 4))))

(defn calc-reset-mesh [^js mesh]
  (let [reset-mesh (.clone mesh)]
    (.setRotationFromEuler reset-mesh (new Euler 0 0 0))
    (.updateMatrixWorld reset-mesh)
    reset-mesh))

(defn static-cube [^js world ^js mesh]
  (-> mesh .-geometry .computeBoundingBox)
  (let [reset-mesh (calc-reset-mesh mesh)
        ^js bounding-box (.getSize (.setFromObject (new Box3) reset-mesh) (new Vector3))
        x-size (/ (.-x bounding-box) 2)
        y-size (/ (.-y bounding-box) 2)
        z-size (/ (.-z bounding-box) 2)
        x (-> mesh .-position .-x)
        y (-> mesh .-position .-y)
        z (-> mesh .-position .-z)
        ^js collider-desc ((-> rapier .-ColliderDesc .-cuboid) x-size y-size z-size)
        ^js collider (.createCollider world (-> collider-desc
                                                (.setTranslation x y z)
                                                (.setRotation #js {:x (-> mesh .-quaternion .-x)
                                                                   :y (-> mesh .-quaternion .-y)
                                                                   :z (-> mesh .-quaternion .-z)
                                                                   :w (-> mesh .-quaternion .-w)})))]
    (sync-mesh-collider mesh collider)
    {:mesh mesh :collider collider}))

(defn rigid-cube [^js world ^js mesh]
  (-> mesh .-geometry .computeBoundingBox)
  (let [reset-mesh (calc-reset-mesh mesh)
        ^js bounding-box (.getSize (.setFromObject (new Box3) reset-mesh) (new Vector3))
        x-size (/ (.-x bounding-box) 2)
        y-size (/ (.-y bounding-box) 2)
        z-size (/ (.-z bounding-box) 2)
        x (-> mesh .-position .-x)
        y (-> mesh .-position .-y)
        z (-> mesh .-position .-z)
        ^js rigid-body-desc (-> rapier .-RigidBodyDesc .dynamic)
        ^js rigid-body (.createRigidBody world (-> rigid-body-desc
                                                   (.setTranslation  x y z)
                                                   (.setRotation #js {:x (-> mesh .-quaternion .-x)
                                                                      :y (-> mesh .-quaternion .-y)
                                                                      :z (-> mesh .-quaternion .-z)
                                                                      :w (-> mesh .-quaternion .-w)})))
        ^js collider-desc ((-> rapier .-ColliderDesc .-cuboid) x-size y-size z-size)
        ^js collider (.createCollider world collider-desc rigid-body)]
    (sync-mesh-collider mesh collider)
    {:mesh mesh :collider collider :rigid-body rigid-body}))

(defn rigid-sphere [^js world ^js mesh]
  (-> mesh .-geometry .computeBoundingSphere)
  (let [^js bounding-sphere (-> mesh .-geometry .-boundingSphere)
        radius (.-radius bounding-sphere)
        x (-> mesh .-position .-x)
        y (-> mesh .-position .-y)
        z (-> mesh .-position .-z)
        ^js rigid-body-desc (.setTranslation (-> rapier .-RigidBodyDesc .dynamic) x y z)
        ^js rigid-body (.createRigidBody world rigid-body-desc)
        ^js collider-desc ((-> rapier .-ColliderDesc .-ball) radius)
        ^js collider (.createCollider world
                                      (-> collider-desc
                                          (.setRestitution  0.5))
                                      rigid-body)]
    (sync-mesh-collider mesh collider)
    {:mesh mesh :collider collider :rigid-body rigid-body}))

(defn app []
  (let [{:keys [^Scene scene
                ^PerspectiveCamera camera
                ^WebGLRenderer renderer]} system

        gravity #js {:x 0.0 :y -9.81 :z 0.0}
        ^js world (new (.-World rapier) gravity)

        ^js debug-material (new LineBasicMaterial #js {:color 0xffffff :vertexColors true})
        ^js debug-geometry (new BufferGeometry)
        ^js debug-lines (new LineSegments debug-geometry debug-material)]
    ;debug lines
    (.add scene debug-lines)
    ; render loop
    (-> (load-async "assets/test-scene.glb")
        (.then (fn [^js gltf]
                 (.add scene (.-scene gltf))
                 (-> gltf .-scene (.updateMatrixWorld true))
                 (let [objects (doall (keep (fn [^js mesh]
                                              (let [extras (.-userData mesh)
                                                    type (.-collider_type extras)
                                                    shape (.-collider_shape extras)]
                                                (case [type shape]
                                                  ["static" "box"] (static-cube world mesh)
                                                  ["dynamic" "box"] (rigid-cube world mesh)
                                                  ["dynamic" "sphere"] (rigid-sphere world mesh)
                                                  nil)))
                                            (-> gltf .-scene .-children)))]
                   (.setAnimationLoop renderer (fn ^js []
                                                 (doseq [{:keys [mesh collider]} objects]
                                                   (sync-mesh-collider mesh collider))
                                                 (debug-render debug-lines world)
                                                 (.step world)
                                                 (.render renderer scene camera)))))))))

(-> (.init rapier)
    (.then app))
