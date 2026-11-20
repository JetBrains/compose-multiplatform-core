These scripts copy a subfolder from "integration" or "integration-release/*" to a destination branch.

1. Check out the commit you want to copy ("integration" or "integration-release/*").

2. Merge the destination branch into the source branch, picking the destination branch's state in case of conflicts in other folders.

3. Call `./copyCompose.sh jb-main` or `./copyCompose.sh release/1.12` (use another script for another folder). The destination branch is required.

This creates integration-copy/$folder/$hash/to-$destination to merge into the destination branch. It is based on the merge base of the current commit and destination branch and contains the copied subfolder.

The script also makes an empty merge of the copy branch into the local integration branch to avoid conflicts in future merges of the destination branch.
