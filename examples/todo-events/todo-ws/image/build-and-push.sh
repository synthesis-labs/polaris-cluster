VERSION=1.0.0
ECR_ROOT=166813201744.dkr.ecr.eu-west-1.amazonaws.com

for image in */
do
  echo Building image ${image%/} ...
  TAG=$ECR_ROOT/${image%/}
  docker build -t $TAG:$VERSION $image $@
  docker push $TAG:$VERSION
done
