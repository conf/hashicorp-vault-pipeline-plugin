IMAGE    ?= vault-plugin-tests:new-vault
M2       ?= $(HOME)/.m2
MVN_FLAGS := -B -Dmaven.artifact.threads=20

.PHONY: help build test plugin clean

help:
	@echo "Targets:"
	@echo "  build   Build the test Docker image ($(IMAGE))"
	@echo "  test    Run the test suite inside the image (mounts $(M2))"
	@echo "  plugin  Package the HPI artifact into ./target"
	@echo "  clean   Remove ./target"

build:
	docker build --progress=plain -t $(IMAGE) .

test:
	docker run --rm -v $(M2):/root/.m2 $(IMAGE)

plugin:
	rm -rf target
	mkdir -p target
	docker run --rm \
		-v $(M2):/root/.m2 \
		-v $(CURDIR)/target:/app/target \
		--entrypoint mvn \
		$(IMAGE) \
		$(MVN_FLAGS) package -DskipTests
	ls -lh target/*.hpi

clean:
	rm -rf target
