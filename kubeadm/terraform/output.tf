output "masters" {
  value = "${join("\n", aws_instance.k8s-master.*.private_ip)}"
}

output "bastion" {
  value = "${join("\n", aws_instance.bastion-server.*.public_ip)}"
}

output "workers" {
  value = "${join("\n", aws_instance.k8s-worker.*.private_ip)}"
}

output "aws_elb_api_fqdn" {
  value = "${module.aws-elb.aws_elb_api_fqdn}:${var.aws_elb_api_port}"
}

output "default_tags" {
  value = "${var.default_tags}"
}
