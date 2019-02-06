resource "aws_subnet" "utility_a" {
  vpc_id     = "${aws_vpc.vpc.id}"
  cidr_block = "${var.public_subnet_a_cidr_block}"
  availability_zone = "${var.az_a}"

  tags = {
    Name = "${var.public_subnet_a_name}"
  }
}

resource "aws_subnet" "utility_b" {
  vpc_id     = "${aws_vpc.vpc.id}"
  cidr_block = "${var.public_subnet_b_cidr_block}"
  availability_zone = "${var.az_b}"

  tags = {
    Name = "${var.public_subnet_b_name}"
  }
}

resource "aws_subnet" "utility_c" {
  vpc_id     = "${aws_vpc.vpc.id}"
  cidr_block = "${var.public_subnet_c_cidr_block}"
  availability_zone = "${var.az_c}"

  tags = {
    Name = "${var.public_subnet_c_name}"
  }
}
resource "aws_subnet" "private_a" {
  vpc_id     = "${aws_vpc.vpc.id}"
  cidr_block = "${var.private_subnet_a_cidr_block}"
  availability_zone = "${var.az_a}"

  tags = {
    Name = "${var.private_subnet_a_name}"
  }
}

resource "aws_subnet" "private_b" {
  vpc_id     = "${aws_vpc.vpc.id}"
  cidr_block = "${var.private_subnet_b_cidr_block}"
  availability_zone = "${var.az_b}"

  tags = {
    Name = "${var.private_subnet_b_name}"
  }
}

resource "aws_subnet" "private_c" {
  vpc_id     = "${aws_vpc.vpc.id}"
  cidr_block = "${var.private_subnet_c_cidr_block}"
  availability_zone = "${var.az_c}"

  tags = {
    Name = "${var.private_subnet_c_name}"
  }
}
