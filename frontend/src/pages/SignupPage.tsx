import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/features/auth/useAuth';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';

const signupSchema = z.object({
  email: z.string().email('Invalid email format.'),
  password: z.string().min(8, 'Password must be at least 8 characters.'),
});

type SignupFormValues = z.infer<typeof signupSchema>;

export function SignupPage() {
  const navigate = useNavigate();
  const { signup } = useAuth();

  const form = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = async (values: SignupFormValues) => {
    try {
      await signup(values);
      navigate('/verify?status=check-email');
    } catch (err: unknown) {
      const apiErr = (err as { response?: { data?: { code?: string; detail?: string } } })?.response?.data;
      if (apiErr?.code === 'auth.invalid_email') {
        form.setError('email', { message: apiErr.detail || 'Invalid email.' });
      } else if (apiErr?.code === 'auth.weak_password') {
        form.setError('password', { message: apiErr.detail || 'Password is too weak.' });
      } else {
        toast.error(apiErr?.detail || 'Something went wrong. Please try again.');
      }
    }
  };

  return (
    <div className="flex justify-center mt-12 animate-slide-up">
      <Card className="w-full max-w-md transition-shadow hover:shadow-lg">
        <CardHeader>
          <CardTitle className="text-2xl">Sign Up</CardTitle>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField
                control={form.control}
                name="email"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Email</FormLabel>
                    <FormControl>
                      <Input type="email" placeholder="you@example.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" placeholder="••••••••" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting ? 'Creating account...' : 'Create Account'}
              </Button>
            </form>
          </Form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="underline text-foreground">
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
