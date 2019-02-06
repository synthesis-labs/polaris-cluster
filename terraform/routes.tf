resource "aws_route_table" "public_rt" {
  vpc_id     = "${aws_vpc.vpc.id}"

  route {
    ipv6_cidr_block        = "::/0"
    gateway_id = "${aws_internet_gateway.igw.id}"
  }

  tags = {
      Name = "${var.public_rt_name}"
  }
}


resource "aws_route_table" "private_rt_a" {
  vpc_id     = "${aws_vpc.vpc.id}"
  tags = {
      Name = "${var.private_rt_a_name}"
  }

  route {
      ipv6_cidr_block = "::/0"
      nat_gateway_id = "${aws_nat_gateway.nat_a.id}"
  }
}

resource "aws_route_table" "private_rt_b" {
  vpc_id     = "${aws_vpc.vpc.id}"
  tags = {
      Name = "${var.private_rt_b_name}"
  }

  route {
    ipv6_cidr_block = "::/0"
    nat_gateway_id = "${aws_nat_gateway.nat_b.id}"
  }
}

resource "aws_route_table" "private_rt_c" {
  vpc_id     = "${aws_vpc.vpc.id}"
  tags = {
      Name = "${var.private_rt_c_name}"
  }

  route {
      ipv6_cidr_block = "::/0"
      nat_gateway_id = "${aws_nat_gateway.nat_c.id}"
  }
}

resource "aws_route_table_association" "public_rt_association_a" {
  subnet_id      = "${aws_subnet.utility_a.id}"
  route_table_id = "${aws_route_table.public_rt.id}"
}

resource "aws_route_table_association" "public_rt_association_b" {
  subnet_id      = "${aws_subnet.utility_b.id}"
  route_table_id = "${aws_route_table.public_rt.id}"
}

resource "aws_route_table_association" "public_rt_association_c" {
  subnet_id      = "${aws_subnet.utility_c.id}"
  route_table_id = "${aws_route_table.public_rt.id}"
}

resource "aws_route_table_association" "private_rt_association_a" {
  subnet_id      = "${aws_subnet.private_a.id}"
  route_table_id = "${aws_route_table.private_rt_a.id}"
}

resource "aws_route_table_association" "private_rt_association_b" {
  subnet_id      = "${aws_subnet.private_b.id}"
  route_table_id = "${aws_route_table.private_rt_b.id}"
}
resource "aws_route_table_association" "private_rt_association_c" {
  subnet_id      = "${aws_subnet.private_c.id}"
  route_table_id = "${aws_route_table.private_rt_c.id}"
}