node {
   stage 'Checkout'

   checkout scm

   stage 'Build'

   sh "rm -rf build/libs/"
   sh "chmod +x gradlew"
   sh "./gradlew build --refresh-dependencies"

   stage "Archive artifacts"

   sh "./gradlew publish"
}