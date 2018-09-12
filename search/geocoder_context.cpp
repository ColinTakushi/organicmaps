#include "search/geocoder_context.hpp"

#include "search/token_range.hpp"

#include "base/assert.hpp"
#include "base/stl_helpers.hpp"

using namespace std;

namespace search
{
// static
BaseContext::TokenType BaseContext::FromModelType(Model::Type type)
{
  switch (type)
  {
  case Model::TYPE_POI: return TOKEN_TYPE_POI;
  case Model::TYPE_BUILDING: return TOKEN_TYPE_BUILDING;
  case Model::TYPE_STREET: return TOKEN_TYPE_STREET;
  case Model::TYPE_UNCLASSIFIED: return TOKEN_TYPE_UNCLASSIFIED;
  case Model::TYPE_VILLAGE: return TOKEN_TYPE_VILLAGE;
  case Model::TYPE_CITY: return TOKEN_TYPE_CITY;
  case Model::TYPE_STATE: return TOKEN_TYPE_STATE;
  case Model::TYPE_COUNTRY: return TOKEN_TYPE_COUNTRY;
  case Model::TYPE_COUNT: return TOKEN_TYPE_COUNT;
  }
  CHECK_SWITCH();
}

// static
BaseContext::TokenType BaseContext::FromRegionType(Region::Type type)
{
  switch (type)
  {
  case Region::TYPE_STATE: return TOKEN_TYPE_STATE;
  case Region::TYPE_COUNTRY: return TOKEN_TYPE_COUNTRY;
  case Region::TYPE_COUNT: return TOKEN_TYPE_COUNT;
  }
  CHECK_SWITCH();
}

size_t BaseContext::SkipUsedTokens(size_t curToken) const
{
  while (curToken != m_tokens.size() && IsTokenUsed(curToken))
    ++curToken;
  return curToken;
}

bool BaseContext::IsTokenUsed(size_t token) const
{
  ASSERT_LESS(token, m_tokens.size(), ());
  return m_tokens[token] != TOKEN_TYPE_COUNT;
}

bool BaseContext::AllTokensUsed() const
{
  for (size_t i = 0; i < m_tokens.size(); ++i)
  {
    if (!IsTokenUsed(i))
      return false;
  }
  return true;
}

bool BaseContext::HasUsedTokensInRange(TokenRange const & range) const
{
  ASSERT(range.IsValid(), (range));
  for (size_t i = range.Begin(); i < range.End(); ++i)
  {
    if (IsTokenUsed(i))
      return true;
  }
  return false;
}

size_t BaseContext::NumUnusedTokenGroups() const
{
  size_t numGroups = 0;
  for (size_t i = 0; i < m_tokens.size(); ++i)
  {
    if (!IsTokenUsed(i) && (i == 0 || IsTokenUsed(i - 1)))
      ++numGroups;
  }
  return numGroups;
}

string DebugPrint(BaseContext::TokenType type)
{
  switch (type)
  {
  case BaseContext::TOKEN_TYPE_POI: return "POI";
  case BaseContext::TOKEN_TYPE_BUILDING: return "BUILDING";
  case BaseContext::TOKEN_TYPE_STREET: return "STREET";
  case BaseContext::TOKEN_TYPE_UNCLASSIFIED: return "UNCLASSIFIED";
  case BaseContext::TOKEN_TYPE_VILLAGE: return "VILLAGE";
  case BaseContext::TOKEN_TYPE_CITY: return "CITY";
  case BaseContext::TOKEN_TYPE_STATE: return "STATE";
  case BaseContext::TOKEN_TYPE_COUNTRY: return "COUNTRY";
  case BaseContext::TOKEN_TYPE_POSTCODE: return "POSTCODE";
  case BaseContext::TOKEN_TYPE_COUNT: return "COUNT";
  }
  CHECK_SWITCH();
}
}  // namespace search
