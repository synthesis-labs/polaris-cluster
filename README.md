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

Polaris is an open-source, opiniated & validated architecture for hyper-scale enterprise clusters.

It has the following features:

- Kubernetes
- Cilium
- CoreOS (CoreOS-stable-1855.4.0-hvm)
- RBAC enabled
- Helm installed
- Includes Prometheus Operator (& Kube-Prometheus collectors)
- Grafana pre-configured with basic graphs
- Ingress-controller setup
- External DNS to Route53
- Cluster Autoscaler enabled
- DEX & Static Password login (for kubectl credential)
- Flux for CD pipeline and automated deployments
- AWS Service Operator installed (for auto-creation of ECRs)

# Provisioning a polaris cluster

1.