import jszip from "jszip";

const createInstance = () => new jszip();

export const loadAsync = jszip.loadAsync;

export default createInstance;
