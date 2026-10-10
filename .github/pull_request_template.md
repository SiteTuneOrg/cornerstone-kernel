<!-- Plain language, for the person who merges. Delete what does not apply. -->

**Before:** what the kernel accepted, refused or computed.

**After:** what changes for a caller of the public API.

**How:** the approach in a few lines; the `README.md` section that records the decision.

## Checklist

- [ ] The repository's gate passes locally (`./mvnw verify`, see `CLAUDE.md`).
- [ ] A new failure mode is a new `LedgerException` subclass, listed in `permits`, the Javadoc and both READMEs.
- [ ] `README.md` and `README.pt-BR.md` stay in sync.
