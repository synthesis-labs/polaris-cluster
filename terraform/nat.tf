resource "aws_eip" "eip_nat_a" {}
resource "aws_eip" "eip_nat_b" {}
resource "aws_eip" "eip_nat_c" {}

resource "aws_nat_gateway" "nat_a" {
  allocation_id = "${aws_eip.eip_nat_a.id}"
  subnet_id     = "${aws_subnet.utility_a.id}"

  tags = {
    Name = "${var.nat_a_name}"
  }
}

resource "aws_nat_gateway" "nat_b" {
  allocation_id = "${aws_eip.eip_nat_b.id}"
  subnet_id     = "${aws_subnet.utility_b.id}"

  tags = {
    Name = "${var.nat_b_name}"
  }
}

resource "aws_nat_gateway" "nat_c" {
  allocation_id = "${aws_eip.eip_nat_c.id}"
  subnet_id     = "${aws_subnet.utility_c.id}"

  tags = {
    Name = "${var.nat_c_name}"
  }
}