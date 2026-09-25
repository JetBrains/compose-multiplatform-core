# Project: Compose Multiplatform Core - Material3 

This directory contains the core implementation of the Material 3 design system
for Compose Multiplatform. All code generated or modified must strictly follow the
Material 3 Spec and AOSP coding standards.

**Refer to [compose/AGENTS.md](../../AGENTS.md) for general Compose instructions.**

## Compose Multiplatform Material3 aspects
- Compose Multiplatform doesn't introduce separate / platform-specific Material3 widgets.  
- The purpose of CMP Material3 fork is to implement expect/actual declarations for non-android kotlin targets. Most of it is in `skikoMain` source set.  
