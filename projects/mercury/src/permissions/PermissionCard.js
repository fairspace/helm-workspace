import React, {useContext, useState} from 'react';
import {ExpandMore} from "@material-ui/icons";
import {
    Avatar,
    Box,
    Card,
    CardContent,
    CardHeader,
    Checkbox,
    Collapse,
    FormControl,
    FormControlLabel,
    FormGroup,
    FormHelperText,
    FormLabel,
    IconButton,
    List,
    ListItem,
    MenuItem,
    Select,
    Typography,
    withStyles
} from "@material-ui/core";
import classnames from "classnames";

import PermissionViewer from "./PermissionViewer";
import {camelCaseToWords} from "../common/utils/genericUtils";
import CollectionsContext from "../collections/CollectionsContext";
import ConfirmationDialog from "../common/components/ConfirmationDialog";
import ErrorDialog from "../common/components/ErrorDialog";
import type {AccessLevel, AccessMode} from '../collections/CollectionAPI';
import {accessLevels, Collection} from "../collections/CollectionAPI";
import {
    accessLevelForCollection,
    collectionAccessIcon,
    getPrincipalsWithCollectionAccess
} from "../collections/collectionUtils";
import type {User} from '../users/UsersAPI';
import type {Workspace} from '../workspaces/WorkspacesAPI';

const styles = theme => ({
    expand: {
        transform: 'rotate(0deg)',
        marginLeft: 'auto',
        transition: theme.transitions.create('transform', {
            duration: theme.transitions.duration.shortest,
        }),
    },
    expandOpen: {
        transform: 'rotate(180deg)',
    },
    permissionsCard: {
        marginTop: 10
    },

    avatar: {
        width: 20,
        height: 20,
        display: 'inline-block',
        verticalAlign: 'middle',
        margin: '0 4px'
    },
    additionalCollaborators: {
        display: 'inline-block',
        lineHeight: '20px',
        verticalAlign: 'middle',
        margin: '0 4px'
    },
    property: {
        marginTop: 10
    },
    group: {
        marginLeft: 20
    },
    accessIcon: {
        verticalAlign: 'middle'
    },
    accessName: {
        marginRight: 10,
        marginLeft: 5
    }
});

type PermissionCardProperties = {
    classes?: any;
    collection: Collection;
    users: User[];
    workspaceUsers: User[];
    workspaces: Workspace[];
    maxCollaboratorIcons?: number;
    setBusy?: (boolean) => void;
}

export const PermissionCard = (props: PermissionCardProperties) => {
    const {classes, collection, users, workspaceUsers, workspaces, maxCollaboratorIcons = 5, setBusy} = props;
    const [expanded, setExpanded] = useState(false);
    const [changingAccessMode, setChangingAccessMode] = useState(false);
    const [selectedAccessMode, setSelectedAccessMode] = useState(collection.accessMode);

    const ownerWorkspaceAccess = collection.workspacePermissions.find(p => p.iri === collection.ownerWorkspace)
        ? collection.workspacePermissions.find(p => p.iri === collection.ownerWorkspace).access : "None";
    const [changingOwnerWorkspaceAccess, setChangingOwnerWorkspaceAccess] = useState(false);
    const [selectedOwnerWorkspaceAccess, setSelectedOwnerWorkspaceAccess] = useState(ownerWorkspaceAccess);
    const {setAccessMode, setPermission} = useContext(CollectionsContext);

    const toggleExpand = () => setExpanded(!expanded);
    const collaboratingUsers = getPrincipalsWithCollectionAccess(users, collection.userPermissions);
    const collaboratingWorkspaces = getPrincipalsWithCollectionAccess(workspaces, collection.workspacePermissions);

    const availableWorkspaceMembersAccessLevels = accessLevels.filter(a => a !== "List");

    const handleSetAccessMode = (newMode) => {
        if (collection.canManage) {
            setSelectedAccessMode(newMode);
            setChangingAccessMode(true);
        }
    };

    const handleCancelSetAccessMode = () => {
        setChangingAccessMode(false);
    };

    const handleConfirmSetAccessMode = () => {
        setBusy(true);
        setAccessMode(collection.name, selectedAccessMode)
            .then(handleCancelSetAccessMode)
            .catch(() => ErrorDialog.showError(
                "An error occurred while setting an access mode",
                () => handleConfirmSetAccessMode()
            ))
            .finally(setBusy(false));
    };

    const handleSetOwnerWorkspaceAccess = (event) => {
        if (collection.canManage) {
            setSelectedOwnerWorkspaceAccess(event.target.value);
            setChangingOwnerWorkspaceAccess(true);
        }
    };

    const handleCancelSetOwnerWorkspaceAccess = () => {
        setChangingOwnerWorkspaceAccess(false);
    };

    const handleConfirmSetOwnerWorkspaceAccess = () => {
        setBusy(true);
        setPermission(collection.name, collection.ownerWorkspace, selectedOwnerWorkspaceAccess)
            .then(handleCancelSetOwnerWorkspaceAccess)
            .catch(() => ErrorDialog.showError(
                "An error occurred while setting an access level",
                () => handleConfirmSetOwnerWorkspaceAccess()
            ))
            .finally(setBusy(false));
    };

    const permissionIcons = collaboratingUsers
        .slice(0, maxCollaboratorIcons)
        .map(({iri, name}) => (
            <Avatar
                key={iri}
                title={name}
                src="/public/images/avatar.png"
                className={classes.avatar}
            />
        ));

    const cardHeaderAction = (
        <>
            {permissionIcons}
            {collaboratingUsers.length > maxCollaboratorIcons ? (
                <div className={classes.additionalCollaborators}>
                    + {collaboratingUsers.length - maxCollaboratorIcons}
                </div>
            ) : ''}
            <IconButton
                className={classnames(classes.expand, {
                    [classes.expandOpen]: expanded,
                })}
                onClick={toggleExpand}
                aria-expanded={expanded}
                aria-label="Show more"
                title="Access"
            >
                <ExpandMore/>
            </IconButton>
        </>
    );

    const confirmationMessageForAccessMode = (accessMode: AccessMode) => {
        switch (accessMode) {
            case 'Restricted':
                return (
                    <span>
                        Are you sure you want to change the view mode of
                        collection <em>{collection.name}</em> to <b>{camelCaseToWords(accessMode)}</b>?<br/>
                        Metadata and data files will only be findable and readable for users
                        that have been granted access to the collection explicitly.
                    </span>
                );
            case 'MetadataPublished':
                return (
                    <span>
                        Are you sure you want to <b>publish the metadata</b> of collection <em>{collection.name}</em>?<br/>
                        The metadata will be findable and readable for all users with access to public data.
                    </span>
                );
            case 'DataPublished':
                return (
                    <span>
                        Are you sure you want to <b>publish all data</b> of collection <em>{collection.name}</em>?<br/>
                        The data will be findable and readable for all users with access to public data.<br/>
                        <strong>
                            Warning: This action cannot be reverted.
                            Once published, the collection cannot be unpublished, moved or deleted.
                        </strong>
                    </span>
                );
            default:
                throw Error(`Unknown access mode: ${accessMode}`);
        }
    };

    const renderAccessModeChangeConfirmation = () => (
        <ConfirmationDialog
            open
            title="Confirmation"
            content={confirmationMessageForAccessMode(selectedAccessMode)}
            dangerous
            agreeButtonText="Confirm"
            onAgree={handleConfirmSetAccessMode}
            onDisagree={handleCancelSetAccessMode}
            onClose={handleCancelSetAccessMode}
        />
    );

    function accessModeIsMetaData() {
        return selectedAccessMode === 'MetadataPublished' || selectedAccessMode === 'DataPublished';
    }

    function accessModeIsData() {
        return selectedAccessMode === 'DataPublished';
    }

    function DisableShowMetaDataModeCheckbox() {
        return selectedAccessMode === 'DataPublished';
    }

    function DisableShowDataModeCheckbox() {
        return collection.status !== 'Archived' || selectedAccessMode === 'DataPublished';
    }

    const SetAccessModeFromMetaCheckbox = (event) => {
        if (collection.canManage &&
            selectedAccessMode === 'Restricted' &&
            event.target.checked) {
            handleSetAccessMode('MetadataPublished');
        }

        if (collection.canManage &&
            selectedAccessMode === 'MetadataPublished' &&
            !event.target.checked) {
            handleSetAccessMode('Restricted');
        }
    }

    const SetAccessModeFromDataCheckbox = (event) => {
        if (collection.canManage &&
            (selectedAccessMode === 'Restricted' || selectedAccessMode === 'MetadataPublished') &&
            event.target.checked) {
            handleSetAccessMode('DataPublished');
        }

        if (collection.canManage &&
            selectedAccessMode === 'DataPublished' &&
            !event.target.checked) {
            handleSetAccessMode('MetadataPublished');
        }
    }

    const renderAccessMode = () => (
        <FormControl className={classes.property}>
            <FormLabel>All Users</FormLabel>
            <FormGroup className={classes.group}>
                <FormControlLabel
                    value="Metadata"
                    control={<Checkbox checked={accessModeIsMetaData()} color="primary"/>}
                    label="collection metadata visible for all users"
                    labelPlacement="end"
                    onChange={SetAccessModeFromMetaCheckbox}
                    disabled={DisableShowMetaDataModeCheckbox()}
                />
                <FormControlLabel
                    value="Data"
                    control={<Checkbox checked={accessModeIsData()} color="primary"/>}
                    label="for archived collections, all users can read collection data"
                    labelPlacement="end"
                    onChange={SetAccessModeFromDataCheckbox}
                    disabled={DisableShowDataModeCheckbox()}
                />
                <FormHelperText>Collection AccessMode is: {selectedAccessMode}</FormHelperText>
            </FormGroup>
        </FormControl>
    );

    const renderOwnerWorkspaceAccessChangeConfirmation = () => (
        <ConfirmationDialog
            open
            title="Confirmation"
            content={`Are you sure you want to change all workspace members access to ${selectedOwnerWorkspaceAccess} for ${collection.name} collection?`}
            dangerous
            agreeButtonText="Confirm"
            onAgree={handleConfirmSetOwnerWorkspaceAccess}
            onDisagree={handleCancelSetOwnerWorkspaceAccess}
            onClose={handleCancelSetOwnerWorkspaceAccess}
        />
    );

    const renderOwnerWorkspaceAccess = () => (
        <FormControl className={classes.property}>
            <FormLabel>Workspace Users</FormLabel>
            <Box className={classes.group}>
                <FormGroup>
                    {collection.canManage ? (
                        <Select
                            value={ownerWorkspaceAccess}
                            onChange={access => handleSetOwnerWorkspaceAccess(access)}
                            inputProps={{'aria-label': 'Owner workspace access'}}
                        >
                            {availableWorkspaceMembersAccessLevels.map(access => (
                                <MenuItem key={access} value={access}>
                                    <span className={classes.accessIcon}>{collectionAccessIcon(access)}</span>
                                    <span className={classes.accessName}>{access}</span>
                                </MenuItem>
                            ))}
                        </Select>
                    ) : <Typography>{camelCaseToWords(ownerWorkspaceAccess)}</Typography>}
                </FormGroup>
                <FormHelperText>Default access for members of the owner workspace.</FormHelperText>
            </Box>
        </FormControl>
    );

    const accessLevelDescription = (access: AccessLevel): string => {
        switch (access) {
            case 'List':
                return 'You can see which files are available in this collection.';
            case 'Read':
                return 'You can download files from this collection.';
            case 'Write':
                return 'You can upload files and add metadata to this collection.';
            case 'Manage':
                return 'Share the collection with users and workspaces.';
            case 'None':
            default:
                return 'No access';
        }
    };

    const accessLevel = accessLevelForCollection(collection);

    return (
        <Card classes={{root: classes.permissionsCard}}>
            <CardHeader
                action={cardHeaderAction}
                titleTypographyProps={{variant: 'h6'}}
                title={collection.canManage ? 'Manage access' : `${accessLevel} access`}
                avatar={collectionAccessIcon(accessLevel, 'large')}
                subheader={accessLevelDescription(accessLevel)}
            />
            <Collapse in={expanded} timeout="auto" unmountOnExit>
                <CardContent style={{paddingTop: 0}}>
                    <div style={{overflowX: 'auto'}}>
                        <List>
                            <ListItem disableGutters>
                                {renderAccessMode()}
                            </ListItem>
                            <ListItem disableGutters>
                                {renderOwnerWorkspaceAccess()}
                            </ListItem>
                        </List>
                        <PermissionViewer
                            collection={collection}
                            collaboratingUsers={collaboratingUsers}
                            collaboratingWorkspaces={collaboratingWorkspaces}
                            workspaceUsers={workspaceUsers}
                        />
                    </div>
                </CardContent>
                {changingAccessMode && renderAccessModeChangeConfirmation()}
                {changingOwnerWorkspaceAccess && renderOwnerWorkspaceAccessChangeConfirmation()}
            </Collapse>
        </Card>
    );
};

export default withStyles(styles)(PermissionCard);
