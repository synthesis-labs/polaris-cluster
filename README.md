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

1. Generate the DEX Certificate Authority bits

```
$ k8/dex/gen-dex-ca.sh
```

2. Create the cluster definition using kops

```
$ k8/create-cluster.sh
```

3. Edit the cluster to CA cert, OIDC and additional policies:

```
$ kops edit cluster --state=s3://kops-state-bucket --name=example.cluster.k8s

Then add under spec:
  fileAssets:
  - name: dex-ca-cert
    path: /srv/kubernetes/assets/dex-ca-cert.pem
    roles: [Master] # a list of roles to apply the asset to, zero defaults to all
    content: |
      *<< CONTENTS OF k8/dex/ca/dex-ca-cert.pem >>*
  kubeAPIServer:
    oidcIssuerURL: https://dex.example.cluster.k8s
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
4. Edit the node instance group to enable spot instances (Optional - for running cheap).

```
$ kops edit ig nodes --state=s3://kops-state-bucket --name=example.cluster.k8s

Then add under spec:
  maxPrice: "0.10"
  minSize: 1
  maxSize: 6
```

4. Create the cluster.

```
$ kops update cluster --state=s3://kops-state-bucket --name=example.cluster.k8s

... wait for cluster to come up ...

$ watch -d 'kubectl get nodes -o wide; kubectl get pods --all-namespaces'
```

5. Create Polaris Namespace and Install ServiceAccounts, helm and charts.

```
kubectl create namespace polaris

kubectl apply -f k8/serviceaccounts/tiller-serviceaccount.yaml
helm init --service-account helm-tiller --upgrade --debug --wait

helm upgrade --namespace polaris --install polaris k8/charts/polaris
```

6. Setup DEX

```
install the dex certificates:
kubectl create configmap dex-ca --namespace polaris --from-file dex-ca.pem=dex/ca/dex-ca-cert.pem;
kubectl create secret tls dex-ca --namespace polaris --cert=k8/dex/ca/dex-ca-cert.pem --key=dex/ca/dex-ca-key.pem;
kubectl create secret tls dex-tls --namespace polaris --cert=k8/dex/ca/dex-issuer-cert.pem --key=dex/ca/dex-issuer-key.pem;
hit dex on /.well-known/openid-configuration and ensure you get the dex-kube-issuer cert

modify charts/dex-k8s-authenticator/values.yaml and ensure:
1. CA certificate link is set to public in s3
2. CA certificate contents exists in cacerts section (as base64 encoded value)

install a clusterrole for the admin@example.com administrator:
kubectl apply --namespace polaris -f k8/serviceaccounts/admin@example.com.yaml
```

7. Login and get a kubectl token:

```
https://login.example.cluster.k8s

and load up the kube-config as directed (maybe take a backup of existing!)
```

8. Setup Flux for CD

```
Create a code-commit repo in AWS (manually for now...). e.g. kubernetes-example-cluster

Create an IAM user in AWS (manually for now...) - e.g. flux-example-user

Create an HTTPS Git credentials for AWS CodeCommit for that IAM user, and note the
username and password.

Edit charts/flux/values.yaml and ensure you setup the following:
git.url to have the correct username and password, VERY IMPORTANT that the password is URLEncoded! Otherwise you will get weird errors from flux.

kubectl create namespace devops
helm upgrade --namespace devops --install flux k8/charts/flux

and then watch flux log itself connecting and syncing the repository.

You should now be able to:

$ fluxctl --k8s-fwd-ns polaris list-controllers
$ fluxctl --k8s-fwd-ns polaris list-images

Any specs you put in /cluster-repo will be applied to the cluster.

Charts must be in /cluster-repo/charts and a corresponding release/blah.yaml containing
a FluxHelmRelease for it would also be applied.

Cool watch to see stuff happening:
watch -d '
  fluxctl --k8s-fwd-ns polaris -n example list-controllers;
  fluxctl --k8s-fwd-ns polaris -n example list-images -c example:deployment/example-example
'

Then to setup example as an automated deployment:
$ fluxctl --k8s-fwd-ns polaris -n example automate -c example:fluxhelmrelease/example
```

9. Upgrade Cilium to newer version (to avoid a crash when applying CiliumNetworkPolicies):

```
$ kubectl edit deployment daemonset cilium -n kube-system

Change from:
  image: cilium/cilium:v1.0-stable
to:
  image: cilium/cilium:v1.2

Then kill every cilium pod (and have it restart).
```

10. Install aws-service-operator (early beta, but cool for creating ECRs)

```
# Edit values and make sure you have sane values

$ helm install --name=aws-service-operator k8/charts/aws-service-operator

# Test that it's working, but pushing an ECRRepository into the flux pipe or
# manually applying it - then login to AWS and list
```

## Other adminitrative stuff

- Shell access to the cluster (using creators id_rsa):
```
ssh -i ~/.ssh/id_rsa admin@api.example.cluster.k8s
```

Theres it!

