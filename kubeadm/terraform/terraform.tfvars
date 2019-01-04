#Bastion Host
aws_bastion_size = "t2.medium"


#Kubernetes Cluster

aws_kube_master_size = "t2.large"
aws_kube_worker_size = "t2.large"

#Settings AWS ELB

aws_elb_api_port = 6443
k8s_secure_api_port = 6443
kube_insecure_apiserver_address = "0.0.0.0"

default_tags = {
#  Env = "devtest"
#  Product = "kubernetes"
}

inventory_file = "./hosts"

AWS_DEFAULT_REGION = "sa-east-1"
AWS_SSH_KEY_NAME = "martin-sa"
