import { Avatar, type AvatarProps } from '@mantine/core';
import { fileUrl, type MemberSummary } from './memberApi';
import { initials } from './memberFormat';

interface Props extends Omit<AvatarProps, 'src' | 'children'> {
  member: Pick<MemberSummary, 'lastName' | 'firstName' | 'avatarFileId'>;
  /** Overrides the stored avatar (e.g. a freshly uploaded one in a form). */
  fileId?: string | null;
}

/** Avatar from the files module; falls back to initials when absent or not loadable. */
export function MemberAvatar({ member, fileId, ...rest }: Props) {
  const id = fileId === undefined ? member.avatarFileId : fileId;
  return (
    <Avatar src={id ? fileUrl(id) : null} alt="" {...rest}>
      {initials(member)}
    </Avatar>
  );
}
