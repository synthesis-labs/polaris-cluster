# Polaris

*Polaris development currently in **pre-release***

```
              .__               .__
______   ____ |  | _____ _______|__| ______    
\____ \ /  _ \|  | \__  \\_  __ \  |/  ___/    
|  |_> >  <_> )  |__/ __ \|  | \/  |\___ \    
|   __/ \____/|____(____  /__|  |__/____  >    
|__|                    \/ by synthesis \/     

```

# Architecture Principles

The architecture and design of Polaris are governed and benchmarked against the following principles:

## Fully Automated

Full automation of the following tasks, at a number of levels:

  - Physical cluster creation (autoscaling)
  - Physical cluster updates (capacity changes, and also node OS updates)
  - CD deployments of Kubernetes system resources (pods, services, deployments), for example:
    * Installation of Prometheus+Grafana on cluster bootstrap
    * Installation of specific application component versions
    * Deployment of application component versions (not the build thereof, but the update of the Kubernetes    deployment resource)
    * Kubernetes core services hosted by the Kubernetes cluster itself (Kubernetes-on-Kubernetes where possible) allowing for rolling-updates of Kubernetes services through familiar mechanisms

## Batteries Included

Cluster architecture includes the minimum number of hosted services for developers to only worry about the Application. This means that services such as the following are included in the bootstrapping process and allow developers to focus simply on defining the services required to host the application in the cluster:

  - Cluster logging, monitoring & alerting
  - Cluster node scaling
  - Sane default role-based access control
  - Administrative console/dashboard
  - System deployment console
  - System package management
  - Pre-configured container networking

## Vanilla & Core Kubernetes

Preference should be made to technology which is either out-of-the-box within the Kubernetes ecosystem, or that is generally included in a best-practice when using Kubernetes in the enterprise setting. This means wherever possible to avoid building bespoke system or management components, or performing bespoke integration between system or management components which don't naturally align to the Kubernetes core.

## Autoscaling of Everything

Autoscaling in Kubernetes needs to be achieved at two levels:

  - Physical node autoscaling (typically a concern at the cloud provider level)
  - Pod autoscaling (typically a concern at the Kubernetes/application level)

If done correctly, Kubernetes can provide support for both levels of scaling.

## Security Approach

__The ideal cluster is one which does not require any human operators to function, but still supports a development team who require higher levels of visibility during the development phase.__

Loosely we can define a number of levels of access policy (ordered from coarse to fine grained):

  - Cloud provider (access to cloud/physical infrastructure)
  - Node (SSH access to nodes)
  - Pod (kubectl access to exec within a container)
  - System services (webconsole access to system services such as Prometheus, Grafana or Dashboard)
  - Namespaces (same as system services, but limited to particular namespaces)

The objective is that over time we can progress the maturity down the chain, moving more and more towards a model of least privilege and highly granular role-based access. __It is envisioned that this is an evolved approach__.

## Immutable Model

Leveraging revision control for all cluster configuration / bootstrapping / updates. Kubernetes absolutely supports this approach through it's state-sync and resource design allowing all configuration through simple yaml or json files. Ensuring that the deployment pipeline is via a git repository creates a strong *audit trail of changes* and *ability to rollback*. The ability to create clusters in a *deterministic* manner will *reduce human error* and *reduce time-to-deployment*.

The ideal cluster is one which is upgraded through a blue/green deployment model - including updates to the system components themselves (Kubernetes-on-Kubernetes). This is strongly supported through standard deployments on Kubernetes plus via physical infrastructure tools such as kops.

Minimizing the need to make in-flight changes to resources through human tools such as kubectl applies outside of pure sandbox/development environments.

## Platform Agnostic

The cluster can be provisioned on as many platforms as possible to allow for maximum usability and compatibility. This includes the ability to run a cluster __on-premise or in the Cloud__. 

## Extensible and Customizable

The cluster is easy to build upon to the required specifications and can be customized with optional modules to extend the functionality of Kubernetes.

# Architecture Deep Dive

## Platform

### Kubernetes

From the [Kubernetes docs](https://kubernetes.io/docs/concepts/overview/what-is-kubernetes/):
> Kubernetes is a portable, extensible open-source platform for managing containerized workloads and services, that facilitates both declarative configuration and automation. It has a large, rapidly growing ecosystem. Kubernetes services, support, and tools are widely available.

### CoreOS

From the [CoreOS docs](https://coreos.com/why/):
> A lightweight Linux operating system designed for clustered deployments providing automation, security, and scalability for your most critical applications

## Authorization, Authentication and Access Control

### RBAC

RBAC stands for **Role Based Access Control**. RBAC in Kubernetes controls access, visibility and operation execution for both users and resources. In Polaris, DEX (seen below) is used alongside RBAC for authentication and access control respectively.

For more info on RBAC see the [Kubernetes docs](https://kubernetes.io/docs/reference/access-authn-authz/rbac/).

### DEX

Dex is an identity service that allows authentication through identity providers (such as Google, GitHub or Active Directory) via "[connectors](https://github.com/dexidp/dex#connectors)."
Polaris uses the Helm Charts [Dex](https://github.com/helm/charts/tree/master/stable/dex) and [Dex k8s Authenticator](https://github.com/mintel/dex-k8s-authenticator/blob/master/charts/dex-k8s-authenticator/Chart.yaml) to implement Dex.

You can find more info on Dex in the [Dex GitHub repo](https://github.com/dexidp/dex)

## Monitoring

### Prometheus

Prometheus is a tool used for metrics monitoring and alerting. Polaris uses Prometheus to capture, store and query metrics and for alerting. Prometheus and Grafana (seen below) are used hand in hand to handle metrics.

Polaris uses the [kube-prometheus](https://github.com/coreos/prometheus-operator/tree/master/helm/kube-prometheus) and [prometheus-operator](https://github.com/coreos/prometheus-operator/tree/master/helm/prometheus-operator) charts for Prometheus and Grafana implementations. To understand the difference between the two charts please see this [link](https://github.com/coreos/prometheus-operator#prometheus-operator-vs-kube-prometheus).

For more info on Prometheus, view the [docs](https://prometheus.io/docs/introduction/overview/).

### Grafana

Grafana is a tool for metrics analytics and visualization. Polaris uses Grafana to make sense of metrics from Prometheus. Grafana is included in the [kube-prometheus](https://github.com/coreos/prometheus-operator/tree/master/helm/kube-prometheus) Helm chart.

View the [Grafana docs](http://docs.grafana.org/) for more info.

## Networking and Security

### Ingress-controller setup

According to the [Kubernetes docs](https://kubernetes.io/docs/concepts/services-networking/ingress/), Ingress is:

> An API object that manages external access to the services in a cluster, typically HTTP.

Ingress is an easy and efficient way to expose services hosted in a cluster. Polaris makes use of the [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/how-it-works/) using the [nginx-ingress](https://github.com/helm/charts/tree/master/stable/nginx-ingress) Helm chart.

### Cilium

Cilium is a tool for network security filtering in micro-service based applications. It allows developers to create and enforce security policies in a cluster. For more information on Cilium, view the [docs](https://cilium.readthedocs.io/en/stable/).

## Autoscaling

### Cluster Autoscaler

Cluster Autoscaler is a tool that scales a Kubernetes cluster up and down based on resource availability on Nodes. Put simply, it will scale up if there are no nodes with sufficient resources to run pods, and scale down when there is a node that has seen minimal utilization for a period of time and there is space for the node's pods on other nodes.

Polaris makes use of the [cluster-autoscaler](https://github.com/helm/charts/tree/master/stable/cluster-autoscaler) Helm chart.

You can view the [Cluster Autoscaler GitHub repo](https://github.com/kubernetes/autoscaler/tree/master/cluster-autoscaler) for more information.

## CI/CD and Deployments

### Flux

Flux is a tool that simplifies, streamlines and automates deployments and updates on the cluster. Flux synchronizes a running cluster with a git repository and automatically deploys any changes made in git. It is compatible with Kubernetes specs and Helm charts.

Polaris makes use of the [flux](https://github.com/weaveworks/flux/tree/master/chart/flux) helm chart.

The [Flux GitHub repo](https://github.com/weaveworks/flux) has more information.

### Helm

Helm is a package manager that simplifies installation and upgrading of applications on a cluster. Helm uses the concept of __charts__ to manage applications. The Polaris stack is itself a Helm chart that is made up of many other Helm charts. The `values.yaml` file that Helm charts contain make it easy to customize charts to match any environment.

The [Helm website](https://helm.sh/) contains further information and instructions for installing and using Helm.
