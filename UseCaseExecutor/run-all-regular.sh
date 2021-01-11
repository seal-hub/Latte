for f in `ls ../NoidAccessibility/TransDroid/test-guidelines/*.json`; do
	echo $f
	./run-regular.sh $f
done
