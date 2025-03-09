name: Bug Report
description: Something is not working right
title: "[Bug]: "
labels: ["bug"]
assignees:
  - JaKooLit

body:
  - type: checkboxes
    attributes:
      label: Already reported ? *
      description: Before opening a new bug report, please take a moment to search through the current open and closed issues to check if it already exists.
      options:
      - label: I have searched the existing open and closed issues.
        required: true

  - type: dropdown
    id: type
    attributes:
      label: Regression?
      description: "Regression means that something used to work but no longer does."
      options:
        - "Yes"
        - "No"
        - "Not sure"
    validations:
      required: true

  - type: textarea
    id: mcversion
    attributes:
      label: Mod Version
      description: "Please put the version of the mod that you are using here!"
    validations:
      required: true

  - type: textarea
    id: desc
    attributes:
      label: Description
      description: "What went wrong? What exactly happened?"
    validations:
      required: true

  - type: textarea
    id: repro
    attributes:
      label: How to reproduce
      description: "How can someone else reproduce the issue?"
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: logs, images or videos and other Mods
      description: "Anything that can help."
