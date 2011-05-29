#include "SRC_FIRST.hpp"
#include "assert.hpp"

#include "../std/target_os.hpp"
#include "../std/iostream.hpp"

#include <cassert>
#include <exception>

#ifdef OMIM_OS_BADA
  #include <FBaseSys.h>
#endif

namespace my
{
  void OnAssertFailedDefault(SrcPoint const & srcPoint, string const & msg)
  {
#ifdef OMIM_OS_BADA
    AppLog("ASSERT FAILED%s:%d:%s", srcPoint.FileName(), srcPoint.Line(), msg.c_str());
    AppAssert(false);

#else
    std::cerr << "ASSERT FAILED\n" << srcPoint.FileName() << ":" << srcPoint.Line() << "\n"
               << msg << endl;

#ifdef DEBUG
    assert(false);
#else
    throw std::exception(msg.c_str());
#endif

#endif
  }

  void (*OnAssertFailed)(SrcPoint const &, string const &) = &OnAssertFailedDefault;
}
