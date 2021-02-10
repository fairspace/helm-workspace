import React, {useContext} from 'react';
import {Redirect, Route, Switch} from "react-router-dom";

import * as queryString from 'query-string';
import WorkspaceOverview from "../workspaces/WorkspaceOverview";
import Collections from "../collections/CollectionsPage";
import FilesPage from "../file/FilesPage";
import SearchPage from '../search/SearchPage';
import {MetadataWrapper} from '../metadata/LinkedDataWrapper';
import LinkedDataEntityPage from "../metadata/common/LinkedDataEntityPage";
import MetadataOverviewPage from "../metadata/MetadataOverviewPage";
import LinkedDataMetadataProvider from "../metadata/LinkedDataMetadataProvider";
import CollectionSearchResultList from "../collections/CollectionsSearchResultList";
import WorkspacesPage from "../workspaces/WorkspacesPage";
import {isAdmin} from "../users/userUtils";
import UserContext from "../users/UserContext";
import UserRolesPage from "../users/UserRolesPage";
import FeaturesContext from "../common/contexts/FeaturesContext";
import MetadataView from '../metadata/views/MetadataView';
import BreadcrumbsContext from '../common/contexts/BreadcrumbsContext';
import ExternalStoragePage from "../external-storage/ExternalStoragePage";

const getSubject = () => (
    document.location.search ? queryString.parse(document.location.search).iri : null
);

const WorkspaceRoutes = () => {
    const {currentUser} = useContext(UserContext);
    const {isFeatureEnabled} = useContext(FeaturesContext);

    return (
        <Switch>
            <Route path="/workspaces" exact component={WorkspacesPage} />

            <Route path="/workspace" exact component={WorkspaceOverview} />

            <Route
                path="/collections"
                exact
                render={(props) => (
                    <LinkedDataMetadataProvider>
                        <Collections history={props.history} showBreadCrumbs />
                    </LinkedDataMetadataProvider>
                )}
            />

            <Route
                path="/collections/:collection/:path(.*)?"
                render={(props) => (
                    <LinkedDataMetadataProvider>
                        <FilesPage {...props} />
                    </LinkedDataMetadataProvider>
                )}
            />

            <Route
                path="/collections-search"
                render={(props) => (
                    <LinkedDataMetadataProvider>
                        <CollectionSearchResultList {...props} />
                    </LinkedDataMetadataProvider>
                )}
            />

            <Route
                path="/external-storages/:storage"
                render={(props) => (
                    <ExternalStoragePage {...props} />
                )}
            />

            <Route
                path="/metadata-views"
                render={() => (
                    <BreadcrumbsContext.Provider value={{segments: []}}>
                        <LinkedDataMetadataProvider>
                            <MetadataView />
                        </LinkedDataMetadataProvider>
                    </BreadcrumbsContext.Provider>
                )}
            />

            <Route
                path="/metadata"
                exact
                render={() => {
                    if (!currentUser.canViewPublicMetadata) {
                        return null;
                    }

                    const subject = getSubject();
                    if (subject) {
                        return (
                            <MetadataWrapper>
                                <LinkedDataEntityPage title="Metadata" subject={subject} />
                            </MetadataWrapper>
                        );
                    }
                    if (isFeatureEnabled('MetadataEditing')) {
                        return (
                            <MetadataWrapper>
                                <MetadataOverviewPage />
                            </MetadataWrapper>
                        );
                    }
                    return null;
                }}
            />

            <Route
                path="/search"
                render={({location, history}) => <SearchPage location={location} history={history} />}
            />
            <Route
                path="/users"
                exact
                render={() => (isAdmin(currentUser) && (<UserRolesPage />))}
            />

            <Redirect to="/workspaces" />
        </Switch>
    );
};

export default WorkspaceRoutes;
