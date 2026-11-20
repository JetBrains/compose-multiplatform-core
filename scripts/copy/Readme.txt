The purposes of these scripts is to copy some subfolder to jb-main branch from "integration" or "integration-release/*".

1. Checkout the commit you want to copy ("integration" or "integration-release/*")

2. Merge jb-main to integration, pick "jb-main" state in a case of conflicts in other folders

3. Call `./copyCompose.sh` (use another script for another folder)

It creates 2 branches:
- integration-copy/$hash/to-jb-main - should be merged to "jb-main". It is based on merge-base(currentCommit, jb-main) and has the copy of the subfolder from currentCommit
- integration-copy/$hash/to-integration - should be merged to "integration", to avoid conflicts in future merges of jb-main. It is created as "empty" merge of "to-jb-main" to merge-base(currentCommit, integration)
