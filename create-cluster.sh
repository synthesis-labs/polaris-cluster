
CLUSTER_NAME=example.cluster.k8s # name of the cluster
STATE_BUCKET="" # name of s3 bucket where the state of the cluster will live
REGION=eu-west-1  
AVAILABILITY_ZONES=${REGION}a,${REGION}b,${REGION}c # select AZ's - depending on the region but some may only have 2
KUBERNETES_VERSION=1.10.5 
MASTER_ZONES=${REGION}b
CLUSTER_CIDR=10.0.0.0/20

# Newer kernel version for cilium support
IMAGE=595879546273/CoreOS-stable-1855.4.0-hvm

MASTER_COUNT=1
NODE_COUNT=3

# To allow overriding of etcd version (to v3) for cilium
KOPS_FEATURE_FLAGS=SpecOverrideFlag \
kops create cluster \
   ${CLUSTER_NAME}                  `# Name of cluster` \
   --override=cluster.spec.etcdClusters[*].version=3.1.11 `# Specify etcd3 version (instead of etcd v2)` \
   --state s3://${STATE_BUCKET}     `# Name of bucket to store state` \
   `#--dry-run                       # Dont actually do it` \
   `#--output yaml                   # Output of dry-run [yaml | json]` \
   `#--out                           # Stdout redirect` \
   `#--target                        # direct, terraform, cloudformation` \
   `#--yes                           # Specify --yes to immediately create the cluster` \
   --admin-access 0.0.0.0/0         `# CIDRs for Admin API eg kubectl` \
   --api-loadbalancer-type public   `# ELB for Admin API internet facing?` \
   --associate-public-ip=false      `# No public IPs for Masters` \
   --authorization RBAC             `# [AlwaysAllow | RBAC]` \
   `# --bastion                      # No bastion hosts (instance group)` \
   --cloud aws                      `# aws, gce or vsphere` \
   --cloud-labels KUBE-CLUSTER=${CLUSTER_NAME} `# instancegroup tags to apply` \
   --dns public                     `# [public | private]` \
   `#--dns-zone blah` \
   `#--encrypt-etcd-storage` \
   --image ${IMAGE}                 `# Image / AMI to use` \
   --kubernetes-version ${KUBERNETES_VERSION} `# Version of kubernetes to run (defaults to version in channel)` \
   --master-count ${MASTER_COUNT}  `# Number of masters` \
   `#--master-public-name           # Only useful for public masters` \
   `#--master-security-groups       # Existing SGs to apply to masters ` \
   --master-size m4.large         `# Instance type of masters` \
   --master-tenancy default        `# [default | dedicated]` \
   --master-volume-size 30         `# Master volume size in GB` \
   --master-zones ${MASTER_ZONES}  `# Zones` \
   `#--model                        # Figure out what this is` \
   --network-cidr ${CLUSTER_CIDR}  `# CIDR for the cluster VPC` \
   --networking cilium             `# kubenet, classic, external, kopeio-vxlan, kopeio), weave, flannel-vxlan, flannel, flannel-udp, calico, canal, kube-router, romana, amazon-vpc-routed-eni, cilium` \
   --node-count ${NODE_COUNT}      `# Number of worker nodes` \
   `#--node-security-groups         # Existing SGs to apply to nodes` \
   --node-size c4.xlarge           `# Node instance type` \
   `#--node-tenancy default          # [default | dedicated]` \
   --node-volume-size 30           `# Node volume size in GB` \
   `#--project                      # Project to use (must be set on GCE)` \
   `#--ssh-access                   # Restrict SSH access to this CIDR.  If not set, access will not be restricted by IP. (default [0.0.0.0/0])` \
   `#--ssh-public-key               # SSH public key to use (default "~/.ssh/id_rsa.pub")` \
   `#--subnets                      # Set to use shared subnets` \
   --topology private              `# [public | private]` \
   `#--utility-subnets              # Set to use shared utility subnets` \
   `#--vpc                          # Set to use a shared VPC` \
   --zones ${AVAILABILITY_ZONES}   `# Zones in which to run the cluster (nodes?)` \
   $1 $2 $3 $4 $5 $6 $7 $8 $9
