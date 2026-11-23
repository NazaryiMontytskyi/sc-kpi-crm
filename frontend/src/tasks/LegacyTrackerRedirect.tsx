import { Navigate, useParams } from 'react-router-dom';
import { legacyTrackerPath } from './trackerNav';

/** Element of the retired personal-list routes: replaces the old URL with its tracker equivalent (spec §7). */
export function LegacyTrackerRedirect() {
  const { '*': rest = '' } = useParams();
  return <Navigate to={legacyTrackerPath(rest)} replace />;
}
