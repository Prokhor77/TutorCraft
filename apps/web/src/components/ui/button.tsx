import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';

/** Stitch buttons: pill shapes; primary lifts with a violet glow, secondary is a ghost pill, success is emerald. */
export const buttonVariants = cva(
  'inline-flex select-none items-center justify-center gap-2 whitespace-nowrap rounded-full font-sans font-semibold transition-[background-color,box-shadow,transform,color] duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25 disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        primary:
          'lift bg-primary text-primary-foreground shadow-sm hover:bg-primary/95 hover:shadow-glow',
        secondary: 'bg-accent/10 text-primary hover:bg-accent/15',
        success:
          'lift bg-success text-success-foreground shadow-sm hover:bg-success/95 hover:shadow-md',
        ghost: 'text-text hover:bg-accent/10 hover:text-primary',
        soft: 'bg-primary-soft text-primary hover:bg-accent/20',
        danger: 'bg-danger text-danger-foreground shadow-sm hover:bg-danger/90',
        link: 'h-auto rounded-sm px-0 text-primary underline-offset-4 hover:underline',
      },
      size: {
        sm: 'h-8 px-3.5 text-label-md',
        md: 'h-10 px-5 text-label-lg',
        lg: 'h-12 px-7 text-base',
        icon: 'size-10',
        'icon-sm': 'size-8',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants> & { asChild?: boolean; loading?: boolean };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    className,
    variant,
    size,
    asChild = false,
    loading = false,
    disabled,
    children,
    type,
    ...props
  },
  ref,
) {
  const classes = cn(buttonVariants({ variant, size }), className);
  if (asChild) {
    return (
      <Slot ref={ref} className={classes} {...props}>
        {children}
      </Slot>
    );
  }
  return (
    <button
      ref={ref}
      type={type ?? 'button'}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <Loader2 className="animate-spin" aria-hidden /> : null}
      {children}
    </button>
  );
});
