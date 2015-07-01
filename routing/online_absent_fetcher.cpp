#include "online_absent_fetcher.hpp"

#include "defines.hpp"
#include "online_cross_fetcher.hpp"

#include "platform/country_file.hpp"
#include "platform/local_country_file.hpp"

using platform::CountryFile;
using platform::LocalCountryFile;

namespace routing
{
void OnlineAbsentFetcher::GenerateRequest(const m2::PointD & startPoint,
                                          const m2::PointD & finalPoint)
{
  // single mwm case
  if (m_countryFileFn(startPoint) == m_countryFileFn(finalPoint))
    return;
  unique_ptr<OnlineCrossFetcher> fetcher =
      make_unique<OnlineCrossFetcher>(OSRM_ONLINE_SERVER_URL, startPoint, finalPoint);
  m_fetcherThread.Create(move(fetcher));
}

void OnlineAbsentFetcher::GetAbsentCountries(vector<string> & countries)
{
  // Have task check
  if (!m_fetcherThread.GetRoutine())
    return;
  m_fetcherThread.Join();
  for (auto point : static_cast<OnlineCrossFetcher *>(m_fetcherThread.GetRoutine())->GetMwmPoints())
  {
    string name = m_countryFileFn(point);
    auto localFile = m_countryLocalFileFn(name);
    if (!HasOptions(localFile->GetFiles(), TMapOptions::EMapWithCarRouting))
    {
      LOG(LINFO, ("Online recomends to download: ", name));
      countries.emplace_back(move(name));
    }
  }
}
}  // namespace routing
