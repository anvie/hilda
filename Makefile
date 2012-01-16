

dist:
	rm -rf target/scala-*/hilda_*
	sbt compile package proguard

deploy: dist
	mv target/scala-*/hilda_*.min.jar target/hilda.jar
	rsync -avzrh target/hilda.jar updater@reposerver:releases/ --chmod=og=rx
