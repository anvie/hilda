version=$(shell cat build.sbt | grep version | grep -P "\\d+\\.\\d+\\.\\d+" -o)
scala_version=2.9.1

dist:
	rm -rf target/scala-*/hilda_*
	sbt compile package proguard

deploy: dist
	mv target/scala-$(scala_version)/hilda_$(scala_version)-$(version).min.jar target/hilda.jar
	rsync -avzrh target/hilda.jar updater@reposerver:releases/ --chmod=og=rx

sync:
	cp target/scala-2.9.1/hilda_$(scala_version)-$(version).min.jar $(DIGAKU_BASE_DIR)/digaku.devutils/hilda.jar

.PHONY: deploy sync
