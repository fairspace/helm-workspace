import axios from 'axios';

export type ObjectWithIconPath = {
    iconPath: string
};

export type ObjectWithIconObjectUrl = ObjectWithIconPath & {
    icon: string
};

export const getSvgIcon = (object: ObjectWithIconPath): Promise<ObjectWithIconObjectUrl> => {
    if (object.iconPath === null) {
        return Promise.resolve(object);
    }
    return axios
        .get(object.iconPath, {headers: {'Cache-Control': 'Private'}, responseType: 'blob'})
        .then(response => {
            const iconUrl = URL.createObjectURL(response.data);
            return {
                ...object,
                icon: iconUrl.trim()
            };
        })
        .catch(() => null);
};

export const getSvgIcons = (objectList: ObjectWithIconPath[]): Promise<ObjectWithIconObjectUrl[]> => {
    return Promise.all(objectList.map(object => getSvgIcon(object)));
};
