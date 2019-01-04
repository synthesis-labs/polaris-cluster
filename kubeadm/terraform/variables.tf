variable "AWS_PROFILE" {
  description = "AWS CLI Profile name"
}

variable "AWS_SSH_KEY_NAME" {
  description = "Name of the SSH keypair to use in AWS."
}

variable "AWS_DEFAULT_REGION" {
  description = "AWS Region"
}

//General Cluster Settings

variable "aws_cluster_name" {
  description = "Name of AWS Cluster: Kubeadm defaults to 'kubernetes'"
  default = "kubernetes"
}

data "aws_ami" "distro" {
  most_recent = true

  filter {
    name   = "name"
    values = ["RHEL-7.5_HVM_GA-*"]
    #values = ["ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  #owners = ["099720109477"]
  owners = ["309956199498"] #rhel
}

//AWS VPC Variables
variable "aws_vpc_cidr_block" {
  description = "CIDR Block for VPC"
  default     = "172.26.22.0/24"
}

variable "aws_cidr_subnets_private" {
  description = "CIDR Blocks for private subnets in Availability Zones"
  type        = "list"
  default     = ["172.26.22.0/25"]
}

variable "aws_cidr_subnets_public" {
  description = "CIDR Blocks for public subnets in Availability Zones"
  type        = "list"
  default     = ["172.26.22.128/25"]
}

//AWS EC2 Settings
variable "aws_bastion_size" {
  description = "EC2 Instance Size of Bastion Host"
  default     = "t2.small"
}

variable "aws_kube_master_ips" {
  description = "Master Nodes Private IPs"
  type        = "list"
  default     = ["172.26.22.120", "172.26.22.121", "172.26.22.122", "172.26.22.123", "172.26.22.124"]
}

variable "aws_kube_master_size" {
  description = "Instance size of Kube Master Nodes"
  default     = "t2.large"
}

variable "aws_kube_worker_ips" {
  description = "Worker Nodes Private IPs"
  type        = "list"
  default     = ["172.26.22.100"]
}

variable "aws_kube_worker_size" {
  description = "Instance size of Kubernetes Worker Nodes"
  default     = "t2.medium"
}

/*
* AWS ELB Settings
*
*/
variable "aws_elb_api_port" {
  description = "Port for AWS ELB"
}

variable "k8s_secure_api_port" {
  description = "Secure Port of K8S API Server"
}

variable "default_tags" {
  description = "Default tags for all resources"
  type        = "map"
}
