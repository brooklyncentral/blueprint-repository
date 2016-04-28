Brooklyn Community Blueprint Repository
=======================================

Have Brooklyn entities or other catalog items you want to share? Fork this
repository, edit [directory.yaml](directory.yaml) to add a link to your
repository, and submit a pull request! 

## Spec for Catalogued Projects - Version 2

The root of your repository should contain **at least** this file:

| Filename     | Optionality                     | Notes                                                         |
|--------------|---------------------------------|---------------------------------------------------------------|
| catalog.bom  | **Required**                    | See below                                                     |

### catalog.bom

This should be a well-formed and valid Apache Brooklyn catalog file, as described in the Brooklyn [catalog documentation](http://brooklyn.incubator.apache.org/v/latest/ops/catalog/index.html). However, this syntax is extended to include metadata information necessary to the community catalog.

All keys described below **have to be defined within the `brooklyn.catalog` -> `publish` key**.

The catalog metadata part will contain these keys:

| Key              | Optionality | Notes                                                                                                                                                         |
|------------------|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| version          | **Required** | Blueprint version (see user guide). It will inherit from the `brooklyn.catalog` -> `version` key and can be omitted in that case |
| description      | **Required** | Blueprint short description. This should be a twitter-length summary. It will inherit from the `brooklyn.catalog` -> `description` key and can be omitted in that case |
| license_code     | **Required** *(if `licence_url` is not specified)* | A license identifier from the [SPDX license list](http://spdx.org/licenses/) |
| license_url      | **Required** *(if `licence_code` is not specified)* | Absolute URL or relative path to a license file (plain text). |
| icon_url         | Optional    | Absolute URL or relative path to a square image file. It will inherit from the `brooklyn.catalog` -> `iconUrl` key  |
| overview         | Optional    | Relative path to as **markdown file**. This should give a short description of what is being blueprinted, with links where appropriate, and to the most common configuration options. It should not attempt to be exhaustive.<br/><br/>*Note that the requirements for this file are sometimes different to the README.md file which GitHub recognises in the root, but a common pattern is for the README.md also to point at this file.  It is permitted for this to point at README.md if that is appropriate* |
| examples         | Optional    | Relative path to a **markdown file** that describes how to use the catalog item in different configurations. This will typically have embedded YAML files as examples |
| reference        | Optional    | Relative path to a **JSON file** describing the configuration, sensors, and effectors, as produced using the following command:<br/>```~# brooklyn list-objects --yaml /path/to/catalog.bom``` |
| changelog        | Optional    | Relative path to a **markdown file** |
| qa               | Optional    | Relative path to a **YAML file** that contains all test entities to be run by the QA framework. This is not displayed directly in the catalog, but may be used to drive QA and the results of the QA displayed in the catalog |
