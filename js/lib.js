import * as i4 from "@dimforge/rapier3d-compat";
import * as i3 from "three/addons/controls/OrbitControls.js";
import * as i2 from "three/addons/environments/RoomEnvironment.js";
import * as i1 from "three/addons/loaders/GLTFLoader.js";
import * as i0 from "three";

const ALL = {};

globalThis.shadow$bridge = function(name) {
  const ret = ALL[name];
  if (ret == undefined) {
    throw new Error("Dependency: " + name + " not provided by external JS!");
  } else {
    return ret;
  }
};

ALL["@dimforge/rapier3d-compat"] = i4;

ALL["three/addons/controls/OrbitControls.js"] = {
  OrbitControls: i3.OrbitControls
};

ALL["three/addons/environments/RoomEnvironment.js"] = {
  RoomEnvironment: i2.RoomEnvironment
};

ALL["three/addons/loaders/GLTFLoader.js"] = {
  GLTFLoader: i1.GLTFLoader
};

ALL["three"] = {
  WebGLRenderer: i0.WebGLRenderer,
  Quaternion: i0.Quaternion,
  BufferAttribute: i0.BufferAttribute,
  LineSegments: i0.LineSegments,
  Vector3: i0.Vector3,
  ACESFilmicToneMapping: i0.ACESFilmicToneMapping,
  PMREMGenerator: i0.PMREMGenerator,
  BufferGeometry: i0.BufferGeometry,
  Euler: i0.Euler,
  Box3: i0.Box3,
  Scene: i0.Scene,
  LineBasicMaterial: i0.LineBasicMaterial,
  Color: i0.Color,
  PerspectiveCamera: i0.PerspectiveCamera
};
