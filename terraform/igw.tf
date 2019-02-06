resource "aws_internet_gateway" "igw" {
  vpc_id = "${aws_vpc.vpc.vpc_id}"

  tags = {
    Name = "${var.igw_name}"
  }
}

