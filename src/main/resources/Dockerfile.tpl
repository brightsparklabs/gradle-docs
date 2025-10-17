##
 # Image used to build and serve a static documentation site.
 # _____________________________________________________________________________
 #
 # Created by brightSPARK Labs
 # www.brightsparklabs.com
 ##

# -----------------------------------------
# BUILD STAGE: GENERATE PDF FILES
# -----------------------------------------

# This stage is not actually used in the website generation. It is just here to
# allow PDF files to be build in a container, with Graphviz+Vega diagramming tools
# available.
#
# Can be called manually via:
#
#   docker build -t docs -f <GENERATED_DOCKER_FILE> --target builder-pdf .

FROM eclipse-temurin:17.0.9_9-jdk-jammy as builder-pdf

RUN apt update
# Allow gradle-docs plugin to read git details on files.
RUN apt install -y git

# Allow asciidoctor-diagram to render plantuml (dot) diagrams.
RUN apt install -y graphviz

# Allow asciidoctor-diagram to render Vega diagrams.
# nvm needs an interactive shell: https://stackoverflow.com/a/60137919
SHELL ["/bin/bash", "--login", "-i", "-c"]
RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.1/install.sh | bash
RUN source ~/.bashrc
RUN nvm install lts/iron
RUN npm install -g vega-cli

# Get gradle distribution (done separately so Docker caches layer)
WORKDIR /src
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle/
RUN ./gradlew build || true # force sucess as build is expected to fail due to no sources

COPY .git ./.git
COPY src ./src

# Build the PDF files.
RUN ./gradlew bslAsciidoctorPdfVersioned --no-daemon

# -----------------------------------------
# EXPORT STAGE
# -----------------------------------------

# The image produced by this stage can be used if the files are to be exported to
# the file system via: `docker build ... --target pdf-export-stage --output output/`

# Base off scratch so output only contains the PDF files.
FROM scratch AS pdf-export-stage
COPY --from=builder-pdf /src/build/docs/asciidoc .

# -----------------------------------------
# BUILD STAGE: GENERATE ASCIIDOC FILES
# -----------------------------------------

FROM eclipse-temurin:17.0.9_9-jdk-alpine as builder-java

# Directory where to build the image from.
ARG BUILD_IMAGES_DIR="${config.buildImagesDir}"

# Allow gradle-docs plugin to read git details on files.
RUN apk add git

# Get gradle distribution (done separately so Docker caches layer)
WORKDIR /src
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew build || true # force sucess as build is expected to fail due to no sources

COPY .git ./.git
COPY src ./src

# Build the asciidoc files and Dockerfile resources.
RUN ./gradlew jinjaPreProcess generateDockerfile --no-daemon

# Always ensure the images directory exists since it is copied in next stage.
RUN mkdir -p "\${BUILD_IMAGES_DIR}"

# -----------------------------------------
# BUILD STAGE: GENERATE WEBSITE
# -----------------------------------------

FROM node:24-alpine3.21 AS builder-jekyll

# Directory where to build the image from.
ARG BUILD_IMAGES_DIR="${config.buildImagesDir}"

# Install Jekyll dependencies.
# See https://dalwar23.com/how-to-install-jekyll-in-alpine-linux/
RUN apk add ruby-full ruby-dev gcc g++ make pinentry

# Allow asciidoctor-diagram to render plantuml (dot) diagrams.
RUN apk add graphviz

# Allow asciidoctor-diagram to render Vega diagrams.
# NOTE: Vega has a few system level graphics libraries that it requires.
RUN apk add giflib-dev python3 pixman-dev cairo-dev pkgconfig pango-dev
RUN npm install -g vega-cli

# Mermaid to render SVG/PNG/PDF files.
RUN npm install -g @mermaid-js/mermaid-cli

RUN mkdir -p /src
COPY --from=builder-java /src/build/brightsparklabs/docs/dockerfile/_config.yml /src
COPY --from=builder-java /src/build/brightsparklabs/docs/dockerfile/Gemfile .

# Build and cache the gems.
RUN gem install bundler
RUN bundle install

COPY --from=builder-java /src/build/brightsparklabs/docs/jinjaProcessed /src/
COPY --from=builder-java "/src/\${BUILD_IMAGES_DIR}" /src/images

# NOTE:
#
# If you do not specify `-d /tmp/site` it should default to `/srv/jekyll/_site`.
# However, this directory does not seem to get created for some reason. I.e. adding
# the following to the Dockerfile shows that the directory is not present:
#
#   RUN ls -al /srv/jekyll
#
# Explicitly building to `/tmp/site` fixes it.
# NOTE: We need to specify `--source` otherwise it will parse from `/` which contains invalid files.
RUN jekyll build --verbose -d /tmp/site --source /src

# NOTE:
#
# Need to chown it to root otherwise `jenkins` owns it, and the CI server fails next
# stage. See https://circleci.com/docs/2.0/high-uid-error/
RUN chown -R root:root /tmp/site

# -----------------------------------------
# EXPORT STAGE
# -----------------------------------------

# The image produced by this stage can be used if the files are to be exported to
# the file system via: `docker build ... --target export-stage --output output/`

# Base off scratch so output only contains the website files.
FROM scratch AS export-stage
COPY --from=builder-jekyll /tmp/site .

# -----------------------------------------
# FINAL STAGE
# -----------------------------------------

# The image produced by this stage hosts the website.

FROM caddy:2.9.1
MAINTAINER brightSPARK Labs <enquire@brightsparklabs.com>
ARG BUILD_DATE=UNKNOWN
ARG VCS_REF=UNKNOWN
LABEL org.label-schema.name="nswcc-documentation" \
      org.label-schema.description="Image used to host documentation as a static website." \
      org.label-schema.vendor="brightSPARK Labs" \
      org.label-schema.schema-version="1.0.0-rc1" \
      org.label-schema.vcs-url="https://bitbucket.org/brightsparklabs/gradle-docs" \
      org.label-schema.vcs-ref=\${VCS_REF} \
      org.label-schema.build-date=\${BUILD_DATE}
ENV META_BUILD_DATE=\${BUILD_DATE} \
    META_VCS_REF=\${VCS_REF}

COPY --from=builder-jekyll /tmp/site /usr/share/caddy/
COPY --from=builder-pdf /src/build/docs/asciidoc /usr/share/caddy/export
