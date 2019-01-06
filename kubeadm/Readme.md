# Kubeadm Deployed using Ansible

## Getting started
Kubeadm does not provision infrastructure / cloud resources (unlike KOPS).
For this reason, infrastrructure must be pre-previsioned.

#### AWS Infrastructure


# Ansible Deployment Method
#### Update Host Vars
Cluster configuration is stored in groupvars/hosts file.
Ensure that this file correctly reflects the host IPs and names.

#### Reset Cluster (Optional)
Should you need to re-create a cluster or ensure that you are building in a clean environment.
WARNING: This will kill ETCD, K8 and iptables
```
$ ansible-playbook ./reset_cluster_node.yml -i group_vars/hosts.yml
```

#### Generate Cluster Certs
```
$ ansible-playbook ./generate_certs.yml -i group_vars/hosts.yml
```

#### Bootstrap the nodes
```
$ ansible-playbook ./bootstrap_rhel_node.yml -i group_vars/hosts.yml
```

#### Install ETCD
```
$ ansible-playbook ./bootstrap_etcd_master.yml -i group_vars/hosts.yml
```

#### Bootstrap HAproxy on all nodes
```
$ ansible-playbook ./bootstrap_ha_proxy.yml -i group_vars/hosts.yml
```

#### Initialize the cluster
```
$ ansible-playbook ./init_cluster.yml -i group_vars/hosts.yml
```

#### Register nodes to initialized cluster
```
$ ansible-playbook ./init_k8s.yml -i group_vars/hosts.yml
```
