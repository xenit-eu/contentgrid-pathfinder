# ContentGrid Pathfinder - Ingress management for ContentGrid applications

ContentGrid Pathfinder is a Kubernetes controller that creates Ingress resources from ContentGrid application configuration objects.

## Usage

For every managed application, the ContentGrid Management Platform generates multiple ConfigMaps containing the application settings.

Default and custom domain names configured for a managed application are stored under the `contentgrid.routing.domains` key
in ConfigMaps labelled with `app.contentgrid.com/service-type`.

## Installation

A docker image is available at [`ghcr.io/xenit-eu/contentgrid-pathfinder`](https://github.com/xenit-eu/contentgrid-pathfinder/pkgs/container/contentgrid-pathfinder)

Pathfinder requires a Kubernetes ServiceAccount with permissions in two namespaces:

* The namespace which contains managed applications
  * Resource ConfigMap
    * List,Read,Watch: To be able to locate the application configuration
    * Patch,Edit: To register a finalizer that will keep the object alive until generated ingresses are cleaned up
* The namespace which contains ContentGrid Runtime Platform system components
  * Resource Ingress
    * Full permissions: To manage the ingress

<details>
    <summary>Snippet to set up service account</summary>

```yaml
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pathfinder-discovery
  namespace: ${pathfinder.source.namespace}
rules:
  - apiGroups:
      - ""
    resources:
      - configmaps
    verbs:
      - get
      - list
      - watch
      - patch
      - update
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pathfinder-management
  namespace: ${pathfinder.target.namespace}
rules:
  - apiGroups:
      - "networking.k8s.io"
    resources:
      - ingresses
    verbs:
      - get
      - list
      - watch
      - create
      - update
      - patch
      - delete
      - deletecollection
---
```

</details>

## Configuration

Pathfinder is configured by a number of Spring properties.

| Property                                                 | Default                                          | Description                                                                                                                                                                                                                                                                                                     |
|----------------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `pathfinder.kubernetes`                                  | Uses auto-configuration                          | Configuration for the [fabric8 kubernetes client](https://github.com/fabric8io/kubernetes-client). All properties that are available on the `Config` object can be set.<br/>If no properties are set, defaults will be derived from the environment.                                                            |
| `pathfinder.source.namespace`                            | `default`                                        | Kubernetes namespace from which ContentGrid application ConfigMaps will be read.                                                                                                                                                                                                                                |
| `pathfinder.source.labels`                               | `{ [app.contentgrid.com/service-type: gateway }` | Map of labels that must be present on the ConfigMap to be considered as ContentGrid application ConfigMap                                                                                                                                                                                                       |
| `pathfinder.target.namespace`                            | `contentgrid-system`                             | Kubernetes namespace to which Ingress resources will be written.                                                                                                                                                                                                                                                |
| `pathfinder.target.ingress-class-name`                   |                                                  | Kubernetes ingress class to use. If left unset, the default ingress must be set by kubernetes and configuration updates will not change the Ingress `spec.ingressClassName`                                                                                                                                     |
| `pathfinder.target.annotations`                          | `{}`                                             | Map with annotations that are added to each generated Ingress resource.                                                                                                                                                                                                                                         |
| `pathfinder.target.copy-annotations`                     | `{}`                                             | Map with annotations that are copied from the ConfigMap to each generated Ingress resource. If it is absent from the ConfigMap or invalid, the default value will be written to both the Ingress and the ConfigMap.                                                                                             |
| `pathfinder.target.copy-annotations[].default-value`     |                                                  | Default value to use for a copied annotation when there is no annotation on the ConfigMap.                                                                                                                                                                                                                      |
| `pathfinder.target.copy-annotations[].acceptable-values` |                                                  | List of values that are valid for an annotation on the ConfigMap to be copied to the Ingress. In case of an invalid value, the default value will be used for the Ingress (and overwrite the annotation on the ConfigMap)                                                                                       |
| `pathfinder.target.services`                             | `[]`                                             | List of services in `${pathfinder.target.namespace}` that the Ingress will route to. At least one service must be defined to be able to generate Ingress resources.                                                                                                                                             |
| `pathfinder.target.services[].path-type`                 |                                                  | PathType determines the interpretation of the `path` matching. See [Kubernetes Ingress `rules.http.paths.pathType`](https://kubernetes.io/docs/reference/kubernetes-api/service-resources/ingress-v1/#IngressSpec)                                                                                              |
| `pathfinder.target.services[].path`                      |                                                  | Path that is matched against the path of an incoming request. Must begin with a `/`                                                                                                                                                                                                                             |
| `pathfinder.target.services[].service-name`              |                                                  | Name of the Kubernetes service that is used as a backend for the specified path. The service must exist in the target namespace.                                                                                                                                                                                |
| `pathfinder.target.services[].service-port`              |                                                  | Numerical port number on the Service that will be used as a backend. Mutually exclusive with `servicePortName`.                                                                                                                                                                                                 |
| `pathfinder.target.services[].service-port-name`         |                                                  | Name of the port on the Service that will be used as a backend. Mutually exclusive with `servicePort`.                                                                                                                                                                                                          |
| `pathfinder.target.tls.fallback-cn-hostname`             |                                                  | A hostname shorter than 64 characters that points to the used Kubernetes Ingress controller.<br/>It is only used for ensuring that a valid TLS certificate can be generated when all hostnames set in the application ConfigMap are longer than 64 characters.<br/>This parameter is optional, but recommended. |


<details>
    <summary>Example configuration</summary>

```yaml
pathfinder:
  target:
    annotations:
      '[cert-manager.io/cluster-issuer]': letsencrypt
    tls:
      fallback-cn-hostname: managed-by.contentgrid.cloud
    services:
      - path: /
        path-type: Prefix
        service-name: gateway
        service-port-name: http
      - path: /iam
        path-type: Prefix
        service-name: keycloak-http
        service-port: 8080
```

</details>