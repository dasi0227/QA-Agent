import Cropper, { type Area } from "react-easy-crop";
import "react-easy-crop/react-easy-crop.css";

type CropPoint = {
    x: number;
    y: number;
};

type ProfileAvatarCropperProps = {
    image: string;
    crop: CropPoint;
    zoom: number;
    onCropChange: (crop: CropPoint) => void;
    onZoomChange: (zoom: number) => void;
    onCropComplete: (croppedArea: Area, croppedAreaPixels: Area) => void;
};

export type CropArea = Area;

export function ProfileAvatarCropper({
    image,
    crop,
    zoom,
    onCropChange,
    onZoomChange,
    onCropComplete,
}: ProfileAvatarCropperProps) {
    return (
        <Cropper
            image={image}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="rect"
            onCropChange={onCropChange}
            onZoomChange={onZoomChange}
            onCropComplete={onCropComplete}
        />
    );
}
