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

# Overview

Polaris is an open-source, opiniated & validated architecture for hyper-scale enterprise clusters that allows for easy setup of a cluster with all the essentials ready for application development and deployment. The authors of Polaris believe that event-driven microservice architectures will eat the current legacy RESTful request/response world, and therefore a slant towards hyper-scale, streaming technology is evident in the Polaris design. 

Polaris has the following features:

## Platform
- Kubernetes
- CoreOS (CoreOS-stable-1855.4.0-hvm)

## Authorization, Authentication and Access Control
- RBAC enabled
- DEX & Static Password login (for kubectl credential)

## Monitoring
- Includes Prometheus Operator (& Kube-Prometheus collectors)
- Grafana pre-configured with basic graphs

## Networking
- Ingress-controller setup
- External DNS to Route53
- Cilium

## Autoscaling
- Cluster Autoscaler enabled

## CI/CD and Deployments
- Flux for CD pipeline and automated deployments
- Helm installed
- AWS Service Operator installed (for auto-creation of ECRs)

## Streaming
- Confluent Platform Open-source (all components from cp-helm-charts)
- Landoop Schema Registry UI
- Landoop Topics UI
- Landoop Connect UI

# Principles

Polaris is __built, governed__ and __benchmarked__ against the following principles:

- Fully Automated
- Batteries Included
- Core Kubernetes
- Scalable
- Secure
- Immutable
- Platform Agnostic
- Customizable

For a more detailed look at the Principles and Architecture of Polaris please view [ARCHITECTURE.md](./ARCHITECTURE.md).

# Provisioning a polaris cluster on AWS

## What you'll need:

- Registered domain name
- Route53 Hosted Zone
- S3 state bucket
- IAM User with access key for kops with the following permissions:
  * AmazonEC2FullAccess
  * IAMFullAccess
  * AmazonS3FullAccess
  * AmazonVPCFullAccess
  * AmazonRoute53FullAccess

You can view the [kops aws docs](https://github.com/kubernetes/kops/blob/master/docs/aws.md) for more info.

1. Install Helm, a package manager for Kubernetes

```
  The Helm client can be installed either from source, or from pre-built binary releases.
  
  From Snap(Linux)
  $ sudo snap install helm --classic

  macOS
  $ brew install kubernetes-helm

  From Chocolatey
  $ choco install kubernetes-helm
```

2. Generate the DEX Certificate Authority bits

```
$ dex/gen-dex-ca.sh
```

3. Create SSH key that will be used by the Kubernetes cluster 

```
$ ssh-keygen -t rsa
```

4. Create the cluster definition using kops

```
$ create-cluster.sh
```

5. Edit the cluster to CA cert, OIDC and additional policies:

```
$ kops edit cluster --state=s3://kops-state-bucket --name=example.cluster.k8s --yes

Then add under spec:
  fileAssets:
  - name: dex-ca-cert
    path: /srv/kubernetes/assets/dex-ca-cert.pem
    roles: [Master] # a list of roles to apply the asset to, zero defaults to all
    content: |
      *<< CONTENTS OF /dex/ca/dex-ca-cert.pem >>*
  kubeAPIServer:
    oidcIssuerURL: *<< URL FOR DEX HERE (eg. https://dex.example.cluster.k8s) >>*
    oidcClientID: kubectl-access
    oidcUsernameClaim: email
    oidcGroupsClaim: groups
    oidcCAFile: /srv/kubernetes/assets/dex-ca-cert.pem
  additionalPolicies:
    node: |
      [
        {
          "Effect": "Allow",
          "Action": [
            "route53:ListHostedZones",
            "route53:ListResourceRecordSets"
          ],
          "Resource": [
            "*"
          ]
        },
        {
          "Effect": "Allow",
          "Action": [
            "route53:ChangeResourceRecordSets"
          ],
          "Resource": [
            "arn:aws:route53:::hostedzone/*"
          ]
        },
        {
          "Effect": "Allow",
          "Action": [
            "ec2:AttachVolume",
            "ec2:DetachVolume"
          ],
          "Resource": [
            "*"
          ]
        },
        {
          "Effect": "Allow",
          "Action": [
            "autoscaling:DescribeAutoScalingGroups",
            "autoscaling:DescribeAutoScalingInstances",
            "autoscaling:DescribeLaunchConfigurations",
            "autoscaling:DescribeTags",
            "autoscaling:SetDesiredCapacity",
            "autoscaling:TerminateInstanceInAutoScalingGroup"
          ],
          "Resource": "*"
        },
        {
           "Effect": "Allow",
            "Action": [
                "codecommit:BatchGet*",
                "codecommit:Get*",
                "codecommit:Describe*",
                "codecommit:List*",
                "codecommit:GitPull"
            ],
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Action": [
              "sqs:*",
              "sns:*",
              "cloudformation:*",
              "ecr:*",
              "dynamodb:*",
              "s3:*"
            ],
            "Resource": "*"
        }
      ]
```

6. Edit polaris/values.yaml file to change the cluster name,region, etc.

```
Dex and Dex-k8s-authenticator:
- enables RBAC for kubectl accsess (logs user into their cluster), gets the CA
  cert from s3 after the cluster has been created.

Cluster-autoscaler: 
- automatically adjusts the size of a Kubernetes Cluster so that all pods have a
  place to run and there are no unneeded nodes.

nginx-ingress:
- allows simple host or URL based HTTP routing.

Flux:
- watches the changes on ECR and communicates updates to cluster to be
  deployed

polaris-prometheus-operator:
 - Installs prometheus-operator (https://github.com/coreos/prometheusoperator) to create/configure/manage Prometheus clusters atop Kubernetes (i.e.
   The Prometheus Operator for Kubernetes provides easy monitoring definitions
   for Kubernetes services and deployment and management of Prometheus instances.)

charts/polaris:
 - installs the addons with the predefined configurations from the helm
   packages located in the same directory and customized with all the values above

```

7. Edit the node instance group to enable spot instances (Optional - for running cheap).

```
$ kops edit ig nodes --state=s3://kops-state-bucket --name=example.cluster.k8s

Then add under spec:
  maxPrice: "0.10"
  minSize: 1
  maxSize: 6
```

8. Create the cluster.

```
Test run:
$ kops update cluster --state=s3://kops-state-bucket --name=example.cluster.k8s

Apply changes:
$ kops update cluster --state=s3://kops-state-bucket --name=example.cluster.k8s --yes

... wait for cluster to come up ...
$ watch -d 'kubectl get nodes -o wide; kubectl get pods --all-namespaces'
```

9. Create Polaris Namespace and Install ServiceAccounts, helm and charts.

```
$ kubectl create namespace polaris

$ kubectl apply -f k8/serviceaccounts/tiller-serviceaccount.yaml

$ helm init --service-account helm-tiller --upgrade --debug --wait

$ helm upgrade --namespace polaris --install polaris-prometheus-operator charts/prometheus-operator-0.0.29.tgz

$ helm upgrade --namespace polaris --install polaris charts/polaris
```
10. Setup DEX

```
Install the dex certificates:



$ kubectl create configmap dex-ca --namespace polaris --from-file dex-ca.pem=dex/ca/dex-ca-cert.pem

$ kubectl create secret tls dex-ca --namespace polaris --cert=dex/ca/dex-ca-cert.pem --key=dex/ca/dex-ca-key.pem

$ kubectl create secret tls dex-tls --namespace polaris --cert=dex/ca/dex-issuer-cert.pem --key=dex/ca/dex-issuer-key.pem

Hit dex on https://dex.example.cluster.k8s/.well-known/openid-configuration and ensure you get the dex-kube-issuer cert.

Modify charts/dex-k8s-authenticator/values.yaml and ensure:
1. CA certificate link is set to public in S3
2. CA certificate contents exists in cacerts section (as base64 encoded value)

Install a clusterrole for the admin@example.com administrator:

$ kubectl apply --namespace polaris -f k8/serviceaccounts/admin@example.com.yaml
```

11. Login and get a kubectl token:

```
https://login.example.cluster.k8s

Load up the kube-config as directed (maybe take a backup of existing!)
```

12. Setup Flux for CD

```
Create a code-commit repo in AWS (manually for now...) - e.g. kubernetes-example-cluster.

Create an IAM user in AWS (manually for now...) - e.g. flux-example-user.

Create an HTTPS Git credentials for AWS CodeCommit for that IAM user, and note the
username and password.

Edit charts/flux/values.yaml and ensure you setup the following:
git.url to have the correct username and password, VERY IMPORTANT that the password is URLEncoded! Otherwise you will get weird errors from flux.

$ kubectl create namespace devops

$ helm upgrade --namespace devops --install flux k8/charts/flux

Watch flux log itself connecting and syncing the repository.

You should now be able to:

$ fluxctl --k8s-fwd-ns polaris list-controllers

$ fluxctl --k8s-fwd-ns polaris list-images

Any specs you put in /cluster-repo and push will be applied to the cluster.

Charts must be in /cluster-repo/charts and a corresponding release/blah.yaml containing
a FluxHelmRelease for it would also be applied.

Cool watch to see stuff happening:

$ watch -d 'fluxctl --k8s-fwd-ns polaris -n example list-controllers; fluxctl --k8s-fwd-ns polaris -n example list-images -c example:deployment/example-example'

Then to setup example as an automated deployment:

$ fluxctl --k8s-fwd-ns polaris -n example automate -c example:fluxhelmrelease/example
```

13. Upgrade Cilium to newer version (to avoid a crash when applying CiliumNetworkPolicies):

```
$ kubectl edit deployment daemonset cilium -n kube-system

Change from:
  image: cilium/cilium:v1.0-stable
to:
  image: cilium/cilium:v1.2

Then delete every cilium pod (and have it restart).
```

14. Install aws-service-operator (early beta, but cool for creating ECRs)

```
Edit values and make sure you have sane values:

$ helm install --name=aws-service-operator k8/charts/aws-service-operator

Test that it's working by pushing an ECRRepository into the flux pipe or manually applying it - then login to AWS and list.
```

## Other administrative stuff

- Shell access to the cluster (using creators id_rsa):
```
$ ssh -i ~/.ssh/id_rsa admin@api.example.cluster.k8s
```

## Related Polaris Projects

- The Polaris Operator Project https://github.com/synthesis-labs/polaris-operator
- The Polaris CLI Project https://github.com/synthesis-labs/polaris-cli
- The Polaris Scaffolds(List Of Published Polaris Scaffolds) https://github.com/synthesis-labs/polaris-scaffolds

There's it!
