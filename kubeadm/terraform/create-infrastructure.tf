terraform {
  required_version = ">= 0.8.7"
}

provider "aws" {
  profile = "${var.AWS_PROFILE}"
  region  = "${var.AWS_DEFAULT_REGION}"
}

data "aws_availability_zones" "available" {}

/*
* Calling modules who create the initial AWS VPC / AWS ELB
* and AWS IAM Roles for Kubernetes Deployment
*/

module "aws-vpc" {
  source = "modules/vpc"

  aws_cluster_name         = "${var.aws_cluster_name}"
  aws_vpc_cidr_block       = "${var.aws_vpc_cidr_block}"
  aws_avail_zones          = "${data.aws_availability_zones.available.names[0]}"
  aws_cidr_subnets_private = "${var.aws_cidr_subnets_private}"
  aws_cidr_subnets_public  = "${var.aws_cidr_subnets_public}"
  default_tags             = "${var.default_tags}"
}

module "aws-elb" {
  source = "modules/elb"

  aws_cluster_name      = "${var.aws_cluster_name}"
  aws_vpc_id            = "${module.aws-vpc.aws_vpc_id}"
  aws_avail_zones       = "${data.aws_availability_zones.available.names[0]}"
  aws_subnet_ids_public = "${module.aws-vpc.aws_subnet_ids_public}"
  aws_elb_api_port      = "${var.aws_elb_api_port}"
  k8s_secure_api_port   = "${var.k8s_secure_api_port}"
  default_tags          = "${var.default_tags}"
}

module "aws-iam" {
  source = "modules/iam"

  aws_cluster_name = "${var.aws_cluster_name}"
}

/*
* Create Bastion Instances in AWS
*
*/

resource "aws_instance" "bastion-server" {
  ami                         = "${data.aws_ami.distro.id}"
  instance_type               = "${var.aws_bastion_size}"
  count                       = "${length(var.aws_cidr_subnets_public)}"
  associate_public_ip_address = true
  availability_zone           = "${data.aws_availability_zones.available.names[0]}"
  subnet_id                   = "${element(module.aws-vpc.aws_subnet_ids_public,count.index)}"

  vpc_security_group_ids = ["${module.aws-vpc.aws_security_group}"]

  key_name = "${var.AWS_SSH_KEY_NAME}"

  tags = "${merge(var.default_tags, map(
      "Name", "kubernetes-${var.aws_cluster_name}-bastion-${count.index}",
      "Cluster", "${var.aws_cluster_name}",
      "Role", "bastion-${var.aws_cluster_name}-${count.index}"
    ))}"
}

/*
* Create K8s Master and worker nodes and haproxy instances
*
*/

resource "aws_instance" "k8s-master" {
  ami           = "${data.aws_ami.distro.id}"
  instance_type = "${var.aws_kube_master_size}"

  count = "${length(var.aws_kube_master_ips)}"

  availability_zone = "${data.aws_availability_zones.available.names[0]}"
  subnet_id         = "${element(module.aws-vpc.aws_subnet_ids_private,count.index)}"

  vpc_security_group_ids = ["${module.aws-vpc.aws_security_group}"]

  iam_instance_profile = "${module.aws-iam.kube-master-profile}"
  key_name             = "${var.AWS_SSH_KEY_NAME}"

  private_ip = "${element(var.aws_kube_master_ips, count.index)}"

  tags = "${merge(var.default_tags, map(
      "Name", "kubernetes-${var.aws_cluster_name}-master${count.index}",
      "kubernetes.io/cluster/${var.aws_cluster_name}", "member",
      "Role", "master"
    ))}"
}

resource "aws_elb_attachment" "attach_master_nodes" {
  count    = "${length(var.aws_kube_worker_ips)}"
  elb      = "${module.aws-elb.aws_elb_api_id}"
  instance = "${element(aws_instance.k8s-master.*.id,count.index)}"
}

resource "aws_instance" "k8s-worker" {
  ami           = "${data.aws_ami.distro.id}"
  instance_type = "${var.aws_kube_worker_size}"

  count = "${length(var.aws_kube_worker_ips)}"

  availability_zone = "${data.aws_availability_zones.available.names[0]}"
  subnet_id         = "${element(module.aws-vpc.aws_subnet_ids_private,count.index)}"

  vpc_security_group_ids = ["${module.aws-vpc.aws_security_group}"]

  iam_instance_profile = "${module.aws-iam.kube-worker-profile}"
  key_name             = "${var.AWS_SSH_KEY_NAME}"

  private_ip = "${element(var.aws_kube_worker_ips, count.index)}"

  tags = "${merge(var.default_tags, map(
      "Name", "kubernetes-${var.aws_cluster_name}-worker${count.index}",
      "kubernetes.io/cluster/${var.aws_cluster_name}", "member",
      "Role", "worker"
    ))}"
}
