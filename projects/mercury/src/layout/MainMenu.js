import React, {useContext} from 'react';
import {NavLink} from "react-router-dom";
import {Divider, List, ListItem, ListItemIcon, ListItemText} from "@material-ui/core";
import {Assignment, Folder, OpenInNew, VerifiedUser, Widgets} from "@material-ui/icons";
import ServicesContext from "../common/contexts/ServicesContext";
import UserContext from "../users/UserContext";
import {isAdmin} from "../users/userUtils";
import {ENABLE_METADATA_PAGE} from "../constants";

export default () => {
    const {pathname} = window.location;
    const {services} = useContext(ServicesContext);
    const {currentUser} = useContext(UserContext);
    return (
        <>
            <List>
                <ListItem
                    component={NavLink}
                    to="/workspaces"
                    button
                    selected={pathname.startsWith('/workspace')}
                >
                    <ListItemIcon>
                        <Widgets />
                    </ListItemIcon>
                    <ListItemText primary="Workspaces" />
                </ListItem>
                <ListItem
                    key="collections"
                    component={NavLink}
                    to="/collections"
                    button
                    selected={pathname.startsWith('/collections')}
                >
                    <ListItemIcon>
                        <Folder />
                    </ListItemIcon>
                    <ListItemText primary="Collections" />
                </ListItem>
                {ENABLE_METADATA_PAGE && currentUser.canViewPublicMetadata && (
                    <ListItem
                        key="metadata"
                        component={NavLink}
                        to="/metadata"
                        button
                    >
                        <ListItemIcon>
                            <Assignment />
                        </ListItemIcon>
                        <ListItemText primary="Metadata" />
                    </ListItem>
                )}
                {isAdmin(currentUser) && (
                    <ListItem
                        key="users"
                        component={NavLink}
                        to="/users"
                        button
                        selected={pathname.startsWith('/users')}
                    >
                        <ListItemIcon>
                            <VerifiedUser />
                        </ListItemIcon>
                        <ListItemText primary="Users" />
                    </ListItem>
                )}
            </List>

            <div>
                <Divider />
                <List>
                    {
                        Object.keys(services).map(key => (
                            <ListItem button component="a" href={services[key]} key={'service-' + key}>
                                <ListItemIcon>
                                    <OpenInNew />
                                </ListItemIcon>
                                <ListItemText primary={key} />
                            </ListItem>
                        ))
                    }
                </List>
            </div>
        </>
    );
};
