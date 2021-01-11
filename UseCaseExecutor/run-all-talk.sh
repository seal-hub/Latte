for f in `ls ../NoidAccessibility/TransDroid/test-guidelines/*.json`; do
	echo $f
	./run-talk.sh $f
done
