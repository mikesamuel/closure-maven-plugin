#!/bin/bash

echo This is not meant to be run automatically.
# https://dzone.com/articles/why-i-never-use-maven-release
# The Maven release plugin tries to make releasing software a
# breeze. That’s where the plugin authors got it wrong to start
# with. Releases are not something done on a whim. They are carefully
# planned and orchestrated actions, preceded by countless rules and
# followed by more rules.

# TODO: Try to streamline this based on
# https://axelfontaine.com/blog/final-nail.html


exit

set -e

# The demo has 1.8 dependencies even though we build the plugin
# proper with -source/-target 1.7.
if ! (java -version 2>&1) | grep -q 1.8; then
    if [ -x /usr/libexec/java_home ]; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
        echo "Set JAVA_HOME=$JAVA_HOME"
    fi
    if ! (java -version 2>&1) | grep -q 1.8; then
        echo "need java 1.8, not $(java -version 2>& 1 | head -1)"
        false
    fi
fi

# Check out a clean copy of the repo.
cd ~/work \
&& export RELEASE_CLONE="$PWD/closureplugin-release" \
&& rm -rf "$RELEASE_CLONE" \
&& cd "$(dirname "$RELEASE_CLONE")" \
&& git clone git@github.com:mikesamuel/closure-maven-plugin.git \
    "$(basename "$RELEASE_CLONE")" \
&& cd "$RELEASE_CLONE" \
&& git submodule init \
&& git submodule update

# Make sure the build is ok
mvn clean && \
./run_all_tests.sh && \
mvn source:jar javadoc:jar package verify site -Prelease -DperformRelease=true

echo Check https://central.sonatype.org/pages/apache-maven.html#nexus-staging-maven-plugin-for-deployment-and-release
echo and make sure you have the relevant credentials in your ~/.m2/settings.xml

echo
echo Check http://search.maven.org/
echo and make sure that the current POM release number is max.

# Pick a release version, and a new dev version.
export OLD_VERSION="$(mvn -B help:evaluate \
                      -Dexpression=project.version | grep -v '\[INFO\]')"
export NEW_VERSION="$(echo -n "$OLD_VERSION" | perl -pe 's/-SNAPSHOT$//')"
export NEW_DEV_VERSION="$(perl -e '
  $_ = $ARGV[0]; s/^([^.]*\.)(\d+)/$1 . ($2 + 1)/e; print' \
  "$OLD_VERSION")"

echo "
OLD_VERSION    =$OLD_VERSION
NEW_VERSION    =$NEW_VERSION
NEW_DEV_VERSION=$NEW_DEV_VERSION
"


# Update the version
# mvn release:update-versions puts -SNAPSHOT on the end no matter what
# so this is a two step process.
# TODO: axelfontaine above shows how to use the versions plugin.
export VERSION_PLACEHOLDER=99999999999999-SNAPSHOT
mvn -B \
    release:update-versions \
    -DautoVersionSubmodules=true \
    -DdevelopmentVersion="$VERSION_PLACEHOLDER"

find plugin/src/it -name pom.xml \
    | xargs perl -i -pe "s|(?<=<project-under-test.version>)[^<]*(?=</project-under-test.version>)|$VERSION_PLACEHOLDER|g;"

find . -name pom.xml -not -path ./submodules/\* \
    | xargs perl -i.placeholder -pe "s/$VERSION_PLACEHOLDER/$NEW_VERSION/g"

git diff


# A dry run with the updated <version>.
mvn clean && \
./run_all_tests.sh && \
mvn clean source:jar javadoc:jar package verify \
    -DperformRelease=true -Prelease

# Commit and tag
git commit -am "Release candidate $NEW_VERSION"
git tag -m "Release $NEW_VERSION" -s "$NEW_VERSION"
git push origin "$NEW_VERSION"

# Actually deploy.
mvn clean source:jar javadoc:jar package verify deploy:deploy \
    -DperformRelease=true -Prelease


# Use the sonatype dashboard to promote the release candidate
echo '1. Go to oss.sonatype.org'
echo '2. Look under "staging repositories" for one named'
echo '   comgoogleclosure-...'
echo '3. Close it.'
echo '4. Refresh until it is marked "Closed".'
echo '5. Check that its OK.'
echo '6. Release it.'


# Generate the site with the release version
mvn clean site site:stage


# Bump the development version.
for f in $(find . -name pom.xml.placeholder); do
    mv "$f" "$(dirname "$f")"/"$(basename "$f" .placeholder)"
done
find . -name pom.xml -not -path ./submosules/\* \
    | xargs perl -i -pe "s/$VERSION_PLACEHOLDER/$NEW_DEV_VERSION/"

git diff

git checkout master
git commit -am 'bump dev version'
git push origin master


# Publish the site
cd ~/work
export SITE_CLONE="$PWD/closureplugin-site"

rm -rf "$SITE_CLONE" \
&& cd "$(dirname "$SITE_CLONE")" \
&& git clone -b gh-pages git@github.com:mikesamuel/closure-maven-plugin.git \
       "$(basename "$SITE_CLONE")" \
&& cd "$SITE_CLONE"

# Empty all source files.  Later we will git rm any that were not copied
# from site.
find . -type f -not -path ./.git/\* -exec rm '{}' \; -exec touch '{}' \;

cp -r "$RELEASE_CLONE"/target/staging/* "$SITE_CLONE"/

find . -type f -not -path ./.git/\* -exec git add '{}' \;
find . -size 0 -not -path ./.git/\* -exec git rm -f '{}' \;

git commit -m "publish site for $NEW_VERSION"
git push origin gh-pages

