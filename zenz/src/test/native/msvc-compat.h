// GCC's retention annotation has no effect on the Windows-only smoke test.
#ifdef _MSC_VER
#define __attribute__(value)
#endif
