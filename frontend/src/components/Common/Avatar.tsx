import React from 'react'

/**
 * Round avatar with image fallback to initials.
 *
 * Why a shared component:
 *   - Same fallback rules in Header, comments, profile, user lists.
 *   - One place to handle broken/missing images (an <img> with no src is
 *     fine; an <img> with a 404 src triggers the onError fallback).
 */
interface AvatarProps {
  firstName?: string | null
  lastName?: string | null
  /** Relative URL such as /api/users/avatars/user-7-xxx.jpg. Null = initials. */
  imageUrl?: string | null
  /** Pixel size of the avatar. Width and height are kept equal. */
  size?: number
  /** Optional className for outer wrapper (e.g. ring styles). */
  className?: string
  title?: string
}

const Avatar: React.FC<AvatarProps> = ({
  firstName,
  lastName,
  imageUrl,
  size = 40,
  className = '',
  title,
}) => {
  const [broken, setBroken] = React.useState(false)
  const initials =
    `${(firstName?.[0] ?? '').toUpperCase()}${(lastName?.[0] ?? '').toUpperCase()}` || '?'
  const px = `${size}px`
  const fontSize = `${Math.max(10, Math.round(size * 0.4))}px`

  // Reset broken flag when the URL changes (e.g. after re-upload).
  React.useEffect(() => {
    setBroken(false)
  }, [imageUrl])

  const showImage = !!imageUrl && !broken

  return (
    <div
      title={title}
      className={`relative rounded-full overflow-hidden bg-primary-500 text-white flex items-center justify-center font-bold shrink-0 ${className}`}
      style={{ width: px, height: px, fontSize }}
    >
      {showImage ? (
        <img
          src={imageUrl as string}
          alt={initials}
          onError={() => setBroken(true)}
          className="w-full h-full object-cover"
          referrerPolicy="no-referrer"
        />
      ) : (
        <span>{initials}</span>
      )}
    </div>
  )
}

export default Avatar
